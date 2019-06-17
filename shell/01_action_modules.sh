# ActionManager
#SotaAM='/Users/dialog/sota/SotaAM/SotaAM.py'
# PersonManager 
SotaPNS='../SotaPNS/SotaPNS.py'
# ActionDecoder
ActionDecoder='action_decoder.jar'

handler()
{
  echo "successfully stopped."
  kill %1
}

trap handler SIGINT

<< COMMENTOUT
echo "#################################"
echo "running SotaAM... from ${SotaAM}"
python2.7 ${SotaAM} xml/S_AM.xml &  
sleep 1s
echo "OK."
echo "#################################"
echo "running SotaPNS... from ${SotaPNS}"
python2.7 ${SotaPNS} xml/S_PNS.xml & 
sleep 3s
echo "OK."
COMMENTOUT
echo "#################################"
echo "running ActionDecoder... from ${ActionDecoder}"
java -jar ${ActionDecoder} 
sleep 1s
echo "OK."
