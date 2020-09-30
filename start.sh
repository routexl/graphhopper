#!/bin/bash

echo
echo -n "Choose map (n)etherlands (g)reatbritain (d)enmark (e)urope (a)ustralia (p)lanet: "
read map

if [ "$map" == "n" ] ; then 
	map=netherlands
elif [ "$map" == "g" ] ; then
	map=great-britain
elif [ "$map" == "e" ] ; then
	map=europe
elif [ "$map" == "a" ] ; then
	map=australia
elif [ "$map" == "d" ] ; then
	map=denmark
elif [ "$map" == "p" ] ; then
	map=planet
else
	echo "Unknown map..."
	exit 0
fi

if [ "$map" == "planet" ] ; then

	echo 
	echo -n "Update planet data from WS2611? [y/n]: "
	read update

	if [ "$update" == "y" ] ; then
		opt="-aruv --delete --progress"
		rsync $opt paul@192.168.123.158:/vol/graphhopper/planet-latest.osm-gh/ /cygdrive/c/Users/Paul/git/graphhopper/planet-latest.osm-gh/
	fi

	OPT="-Xmx20G -Xms2G"

else
	echo
	echo -n "Clear all and prepare as new? [y/n]: "
	read clearall

	if [ "$clearall" == "y" ] ; then
	
		echo
		echo Clear tools and prepared maps
	
		rm -rf tools/target
		rm -rf target
		rm -rf "$map-latest.osm-gh"		

		maven/bin/mvn -e clean compile
	
  		OPT="-Xmx50G -Xms2G"
	
	else
	  	OPT="-Xmx10G -Xms1G"
	fi
fi

echo
export JAVA_OPTS="$OPT"
./graphhopper.sh web "$map-latest.osm.pbf"
