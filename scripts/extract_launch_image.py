#! python3

# extract_launch_image.py

# This script extracts an image from a downloaded source file in the assets
# directory and renders it into PNG formats at the different resolutions required
# for different versions of the ic_launcher.png file stored in different mipmap-XXXX
# subdirectories under ./app/src/main/res.

# Once this script has run there should be no need to run it again, unless/until
# there is a choice to a different image in place of the original.  It is in
# version control as traceability between the downloaded image and its renders, and
# to save time if a new image is desired.

import zipfile
from PIL import Image

# The launch icon for the project is based on the following graphic, accessed under Vecteezy's
# 'Free License' conditions:
# <a href="https://www.vecteezy.com/free-vector/ride">Ride Vectors by Vecteezy</a>.

# Retrieved from
# https://files.vecteezy.com/system/protected/files/004/261/707/vecteezy_black-silhouette-of-a-horse-on-a-white-background-vector-illustration_4261707.zip?response-content-disposition=attachment%3Bfilename%3Dvecteezy_black-silhouette-of-a-horse-on-a-white-background-vector_4261707.zip&Expires=1733290772&Signature=TivU3KsbVg-djV7KRsNmYmf~-3fbSjZMCpk-TnnQegha~6~xuGHwT4uBWg3vIK7NXE-7mKpYOy9Wjo~cZtQ-pWntNA6RtcFTSbU-oy8WwYIQc5n6LsHqUwecB4dT4UFi-21dRJhItH4OFFCeMdVBCV6BhC-V4jktwfv3N-o80WTqMR~0fkOLOTKsXYJ7kW2spUOAWUzpopX3Lhy1OvgQauK1v-uWQAyvw2e51WXmlQYnYooDARXXR5jitcpBAE5iGHwTihVfCHPzMruiMvXo1BssAobb3z1ncBGWXTPsfMba7RSWyyCHQZ97cUVxuTtpgOVwmaD6uJOPb4m8ZT4tkw__&Key-Pair-Id=K3714PYOSHV3HB
# on 4 December 2024, stored in the /assets directory.
# _IMAGE_NAME="vecteezy_black-silhouette-of-a-horse-on-a-white-background-vector-illustration"
# _IMAGE_NUMBER="4261707"

_ANDROID_LAUNCHER_SIZES = {
    "hdpi": 72,
    "mdpi": 48,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

zf = zipfile.ZipFile(f"./assets/{_IMAGE_NAME}_{_IMAGE_NUMBER}.zip")
jpg_bytes = zf.extract(f"{_IMAGE_NAME}_.jpg")
jpg_image = Image.open(jpg_bytes)
for mipmap_suffix in _ANDROID_LAUNCHER_SIZES.keys():
    size = _ANDROID_LAUNCHER_SIZES[mipmap_suffix]
    scaled_image = jpg_image.convert(mode="L")
    scaled_image = scaled_image.resize( (size, size), )
    scaled_image.save(f"./app/src/main/res/mipmap-{mipmap_suffix}/ic_launcher.png")


