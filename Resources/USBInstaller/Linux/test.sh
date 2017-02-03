#!/bin/bash
file="/etc/udev/rules.d/70-lego.rules"
if [ -f "$file" ]
then
	echo "$file found."
else
	echo "$file not found."
fi
