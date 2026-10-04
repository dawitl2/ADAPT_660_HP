param([string]$Output = 'research/usb-inventory.json')
$ErrorActionPreference = 'Stop'
# Read PnP properties only; no IOCTLs, driver changes or vendor requests.
$driverMap = @{}
Get-CimInstance Win32_PnPSignedDriver | ForEach-Object { $driverMap[$_.DeviceID] = $_ }
$devices = @(Get-PnpDevice -PresentOnly | Where-Object {
    $_.InstanceId -match '^(USB|HID|BTHENUM|BTHHFENUM)\\' -or
    $_.FriendlyName -match 'EPOS|Sennheiser|ADAPT.?660' -or
    $_.Class -in @('AudioEndpoint','MEDIA')
} | ForEach-Object {
    $device = $_
    $properties = @{}
    Get-PnpDeviceProperty -InstanceId $device.InstanceId -ErrorAction SilentlyContinue |
        Where-Object KeyName -in @('DEVPKEY_Device_HardwareIds','DEVPKEY_Device_CompatibleIds',
          'DEVPKEY_Device_BusReportedDeviceDesc','DEVPKEY_Device_Manufacturer',
          'DEVPKEY_Device_Parent','DEVPKEY_Device_LocationPaths','DEVPKEY_Device_Service') |
        ForEach-Object { $properties[$_.KeyName] = $_.Data }
    $deviceVid = $null; $devicePid = $null; $interfaceNumber = $null
    if ($device.InstanceId -match 'VID_([0-9A-F]{4}).*PID_([0-9A-F]{4})') {
        $deviceVid = $Matches[1]; $devicePid = $Matches[2]
    }
    if ($device.InstanceId -match '&MI_([0-9A-F]{2})') { $interfaceNumber = $Matches[1] }
    $interfaceCodes = @($properties['DEVPKEY_Device_CompatibleIds'] | ForEach-Object {
        if ($_ -match 'Class_([0-9A-F]{2})&SubClass_([0-9A-F]{2})&Prot_([0-9A-F]{2})') {
            [ordered]@{ class = $Matches[1]; subclass = $Matches[2]; protocol = $Matches[3] }
        }
    })
    $driver = $driverMap[$device.InstanceId]
    [ordered]@{
        device_id = $device.InstanceId; name = $device.FriendlyName; status = $device.Status
        pnp_class = $device.Class; vid = $deviceVid; pid = $devicePid; interface_number = $interfaceNumber
        interface_codes = $interfaceCodes; properties = $properties
        driver = if ($driver) { [ordered]@{ name=$driver.DriverName; provider=$driver.DriverProviderName;
            version=$driver.DriverVersion; inf=$driver.InfName; signed=$driver.IsSigned } } else { $null }
        hid_report_descriptor = $null
    }
})
$report = [ordered]@{
    schema = 'adapt.usb.inventory.v1'; captured_at_utc = [DateTime]::UtcNow.ToString('o')
    source = 'Windows present-only PnP + signed-driver inventory'
    limitations = @('PnP codes are OS-reported, not raw USB descriptors.',
      'HID report descriptors require optional standard GET_DESCRIPTOR tooling or a capture.',
      'No device state was changed. Device IDs/locations may be private.')
    devices = $devices
}
$fullPath = [IO.Path]::GetFullPath($Output)
$null = New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($fullPath))
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $fullPath -Encoding utf8
Write-Output "Recorded $($devices.Count) present devices to $fullPath"
