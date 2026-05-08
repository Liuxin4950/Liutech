@echo off
echo Starting Docker image export...

if not exist docker-images mkdir docker-images

echo Skipping MySQL image export. Production data is kept in the mysql_data volume.

echo Exporting backend image...
docker save liutech-backend:latest -o docker-images/liutech-backend.tar

echo Exporting AI image...
docker save liutech-ai:latest -o docker-images/liutech-ai.tar

echo Exporting web image...
docker save liutech-web:latest -o docker-images/liutech-web.tar

echo Exporting admin image...
docker save liutech-admin:latest -o docker-images/liutech-admin.tar

echo Exporting nginx image...
docker save liutech-nginx:latest -o docker-images/liutech-nginx.tar

echo Export completed!
echo Files are in docker-images/ directory
pause
