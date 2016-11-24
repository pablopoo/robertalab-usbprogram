# robertalab-usbprogram
Standalone program for connecting the robot hardware to Open Roberta lab using
a usb connection.


### Fast installation with maven

#### Clone the repository and compile

    git clone git://github.com/OpenRoberta/robertalab-usbprogram.git
    cd robertalab-usbprogram/OpenRobertaUSB
    mvn clean install


### Run USB program

   java -jar ./OpenRobertaUSB/target/OpenRobertaUSB-*-SNAPSHOT.jar

For the using the NXT with the USB program use 32bit Java.

### Development notes

You can follow the test status on https://travis-ci.org/OpenRoberta/.

Development happens in the 'develop# branch. Please sent PRs against that
branch.

    git clone git://github.com/OpenRoberta/robertalab-usbprogram.git
    cd robertalab-usbprogram
    git checkout -b develop origin/develop
