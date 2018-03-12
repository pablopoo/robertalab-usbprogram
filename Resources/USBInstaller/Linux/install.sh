#!/bin/bash

dir="$( pwd )"

if [ -f /etc/debian_version ]; then
    dpkg --add-architecture i386
    apt-get update
    apt-get install -y libc6-i386 libxext6:i386 libxrender1:i386 libxtst6:i386 libxi6:i386 libusb-0.1-4:i386
elif [ -f /etc/redhat-release ]; then
    dnf update
    dnf install glibc.i686 libXext.i686 libXrender.i686 libXtst.i686 libXi.i686 libusb.i686
elif [ -f /etc/SuSE-release ]; then
    zypper update
    zypper install glibc-32bit libXext6-32bit libXrender1-32bit libXtst6-32bit libXi6-32bit libusb-0_1-4-32bit 
#elif ... other distributions
else
    dnf update
    dnf install glibc.i686 libXext.i686 libXrender.i686 libXtst.i686 libXi.i686 libusb.i686
fi


file="/etc/udev/rules.d/70-lego.rules"
if [ -f "$file" ]
then
	echo "$file found."
else
  echo "# Lego NXT brick in normal mode" >> "/etc/udev/rules.d/70-lego.rules"
  echo 'SUBSYSTEM=="usb", DRIVER=="usb", ATTRS{idVendor}=="0694", ATTRS{idProduct}=="0002", GROUP="lego", MODE="0660"' >> "/etc/udev/rules.d/70-lego.rules"
  echo '# Lego NXT brick in firmware update mode (Atmel SAM-BA mode)' >> "/etc/udev/rules.d/70-lego.rules"
  echo 'SUBSYSTEM=="usb", DRIVER=="usb", ATTRS{idVendor}=="03eb", ATTRS{idProduct}=="6124", GROUP="lego", MODE="0660"' >> "/etc/udev/rules.d/70-lego.rules"
fi

echo "[Desktop Entry]" > "/usr/share/applications/ORUSB.desktop"
echo "Version=2.0.4" >> "/usr/share/applications/ORUSB.desktop"
echo "Name=Open Roberta USB" >> "/usr/share/applications/ORUSB.desktop"
echo "Exec=java -jar -Dfile.encoding=utf-8 $dir/OpenRobertaUSB.jar" >> "/usr/share/applications/ORUSB.desktop"
echo "Path=$dir" >> "/usr/share/applications/ORUSB.desktop"
echo "Icon=$dir/OR.png" >> "/usr/share/applications/ORUSB.desktop"
echo "Terminal=false" >> "/usr/share/applications/ORUSB.desktop"
echo "Type=Application" >> "/usr/share/applications/ORUSB.desktop"
#echo "StartupNotify=True" >> "/usr/share/applications/ORUSB.desktop"
echo "Categories=Application;Development;" >> "/usr/share/applications/ORUSB.desktop"


chmod u+x "/usr/share/applications/ORUSB.desktop"

groupadd lego
gpasswd -a ${SUDO_USER:-$USER} lego
gpasswd -a ${SUDO_USER:-$USER} dialout
udevadm control --reload-rules

#useradd ${SUDO_USER:-$USER} dialout
exec su -l ${SUDO_USER:-$USER}
