#!/bin/bash

# command: bash convert.sh jar_file_name executable_name

# path = $(echo "$PWD")

# chmod +x name
chmod +x $1
# echo '#!/usr/bin/java -jar' > executable
echo '#!/usr/bin/java -jar' > $2
# cat name >> executable
cat $1 >> $2
# chmod +x executable
chmod +x $2
