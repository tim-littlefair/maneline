# Maneline

A collection of code and data related to the protocols used to control 
Fender Musical Instrument Corporation's Mustang® modelling guitar amplifiers.

## Screenshot

![Web UI for the Maneline application](/assets/MustangIv2-Screenshot.png)

## Youtube introductions

The following Youtube videos introduce the application:

[![Demo of Maneline with Mustang LT40S](http://img.youtube.com/vi/Zl81TAoySjk/0.jpg)](http://www.youtube.com/watch?v=Zl81TAoySjk "Demo of Maneline with Mustang LT40S")

[![Demo of Maneline with Mustang I v2](http://img.youtube.com/vi/bbyxWtg2GnY/0.jpg)](http://www.youtube.com/watch?v=bbyxWtg2GnY "Demo of Maneline with Mustang I v2")

[![Maneline build process](http://img.youtube.com/vi/AtbllsGwlEw/0.jpg)](http://www.youtube.com/watch?v=AtbllsGwlEw "Maneline build process")

## Building and deploying

The third of the three YouTube videos linked in the section above shows how to set up the 
development environment for this project on Linux or macOS, and run the application up
in the development host.

The application is also designed to be deployed onto 64-bit Raspberry Pi devices using
the deployment infrastructure provided by [balena.io](https://balena.io).  

[//]: # (If you have an account on balenaCloud, you should be able to deploy the application to)
[//]: # (your own fleet of devices using the following button:)

[![balena deploy button](https://www.balena.io/deploy.svg)](https://dashboard.balena-cloud.com/deploy?repoUrl=https://hub.balena.io/organizations/gh_tim_littlefair/apps/maneline)

TBD: More information on how Balena deployment works.

## More information

There is a roadmap for this project [in the wiki](https://github.com/tim-littlefair/maneline/wiki/Roadmap)
An earlier version of this README.md file (with a lot more detail, some of which 
may now be out of date), is available to read 
[here](/docs/README-TLDR.md).

## References

There are a number of other repositories on Github and other parts of the Internet 
which contained interesting material related to FMIC modelling amplifiers including:
- https://packages.debian.org/sid/mustang-plug
- https://github.com/brentmaxwell/LtAmp
- https://web.archive.org/web/20200621233329/https://bitbucket.org/piorekf/plug/wiki/Home
- https://github.com/snhirsch/mustang-midi-bridge
- https://fender-mustang-amps-and-fuse.fandom.com/wiki/Fender_Mustang_Amps_and_Fuse_Wiki
- [this archive of preset definitions in Fender FUSE formats, collected before FMIC shut down the web site provided to Fender FUSE users for storing and sharing presets](https://guitarpedaldemos.com/fender-fuse-mustang-v2-archive/)
- [this Google Sheets page containing requests and answers for Mustang presets matching particular artists/songs](https://docs.google.com/spreadsheets/d/1KWvjJ6q6Ora2MqmxtUAWjq9Uerp0Ycza5ouuKuol5HQ/edit?pli=1&gid=0#gid=0)
- The Remuda and Remuda/SC apps published on the Play Store by 
  [Triton Interactive](https://play.google.com/store/apps/developer?id=Triton+Interactive), 
  which are Android apps interoperating with the Mustang 'classic' (I/II/III/IV/V) 
  range of Fender Mustang amps.

  



