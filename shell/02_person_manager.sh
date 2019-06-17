# PersonManager
PersonManager='/Users/dialog/sota/person_manager_recording/PersonManagerNoCarib.py'

handler()
{
  echo "successfully stopped."
  kill %1
}

echo "#################################"
echo "running PersonManager... from ${PersonManager}"
python ${PersonManager}
echo "#################################"
