#!/bin/bash

# Configure and prepare projects for import in Eclipse

#Check that the AceAS/db.pwd file exists
FILE=AceAS/db.pwd
if [ ! -f "$FILE" ]; then
 echo
 echo "Warning: File AceAS/db.pwd is missing!"
 echo
fi

