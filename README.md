# robertalab-usbprogram
Standalone program for connecting the robot hardware to Open Roberta lab using
a usb connection.

### Fast installation with maven

#### Clone the repository and compile

    git clone git://github.com/OpenRoberta/robertalab-usbprogram.git
    cd robertalab-usbprogram/OpenRobertaUSB
    mvn clean install


### Run USB program
For running the USB program use 32bit Java.

    java -jar -Dfile.encoding=utf-8 ./OpenRobertaUSB/target/OpenRobertaUSB-*-SNAPSHOT.jar

#### Linux and NXT
For using the NXT install the following libraries

    sudo apt-get install libc6-i386 libxext6:i386 libxrender1:i386 libXtst6:i386 libXi6:i386 libusb-0.1-4:i386
    
Create /etc/udev/rules.d/70-lego.rules file and add the following lines:

    # Lego NXT brick in normal mode
    SUBSYSTEM=="usb", DRIVER=="usb", ATTRS{idVendor}=="0694", ATTRS{idProduct}=="0002", GROUP="lego", MODE="0660"
    # Lego NXT brick in firmware update mode (Atmel SAM-BA mode)
    SUBSYSTEM=="usb", DRIVER=="usb", ATTRS{idVendor}=="03eb", ATTRS{idProduct}=="6124", GROUP="lego", MODE="0660"

The rules relay on the lego user group. Crate a lego group and add the current user to the group:

    sudo groupadd lego
    sudo gpasswd -a <username> lego
    sudo udevadm control --reload-rules

### Development notes

You can follow the test status on https://travis-ci.org/OpenRoberta/.

Development happens in the 'develop# branch. Please sent PRs against that
branch.

    git clone git://github.com/OpenRoberta/robertalab-usbprogram.git
    cd robertalab-usbprogram
    git checkout -b develop origin/develop
    
### Installer creation
Linux:
- run `mvn clean install` in the `OpenRobertaUSB` directory
- go to `USBInstaller` directory and duplicate the `Linux` directory with `cp -R Linux OpenRobertaUSB`
- run `tar -zcvf OpenRobertaUSBLinux-X.X.X.tar.gz OpenRobertaUSB` with correct version

Windows:
- download [WiX Toolset](https://github.com/wixtoolset/wix3/releases)
- download [WDK 8.1](https://www.microsoft.com/en-us/download/details.aspx?id=42273)
- run `mvn clean install` in the `OpenRobertaUSB` directory
- change version in `setup.wxs` (optionally change WDK path, if non default was used)
- run `build.bat`
- add version number to resulting `.msi` files

Mac (this method is really weird, but it works for now):
- run `mvn clean install`
- go to the `USBInstaller/OSX` directory and run `pkgbuild --root root --identifier de.fraunhofer.iais.roberta.OpenRobertaUSB USB.pkg`
- expand the package with `pkgutil --expand USB.pkg ORUSB.pkg`
- download one of the older [releases](https://github.com/OpenRoberta/robertalab-usbprogram/releases)
- expand the downloaded pkg with `pkgutil --expand OpenRobertaUSBMacOSX-X.X.X USB`
- change the version & size in the `Distribution` file
- replace `ORUSB.pkg` with the newly built one from `USBInstaller/OSX`
- flatten the package with `pkgutil --flatten USB OpenRobertaUSBMacOSX-X.X.X.pkg`


### Third party libraries

This implementation uses jWMI by [Henryranch LLC](http://henryranch.net/software/jwmi-query-windows-wmi-from-java/).
