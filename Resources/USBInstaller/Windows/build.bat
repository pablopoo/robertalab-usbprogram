"%WIX%bin\candle.exe" java.wxs resources.wxs setup.wxs
"%WIX%bin\light.exe" -out OpenRobertaUSBSetupDE.msi -ext WixUIExtension -cultures:de-DE java.wixobj setup.wixobj resources.wixobj -b ./resources -b ./java
"%WIX%bin\light.exe" -out OpenRobertaUSBSetupEN.msi -ext WixUIExtension -cultures:en-US java.wixobj setup.wixobj resources.wixobj -b ./resources -b ./java
@pause