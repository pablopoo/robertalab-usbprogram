#!/bin/bash

dir="$( pwd )"


echo "[Desktop Entry]" > "/usr/share/applications/ORUSB.desktop"
echo "Name= OpenRobertaUSBProgram" >> "/usr/share/applications/ORUSB.desktop"
echo "Exec= java -jar $dir/OpenRobertaUSB.jar" >> "/usr/share/applications/ORUSB.desktop"
echo "Icon= $dir/OR.png" >> "/usr/share/applications/ORUSB.desktop"
echo "Terminal= True" >> "/usr/share/applications/ORUSB.desktop"
echo "Type= Application" >> "/usr/share/applications/ORUSB.desktop"
echo "StartupNotify= True" >> "/usr/share/applications/ORUSB.desktop"

chmod u+x "/usr/share/applications/ORUSB.desktop"

