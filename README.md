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
