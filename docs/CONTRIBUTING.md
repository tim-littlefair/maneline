# How to contribute

maneline is at present a personal passion project founded and controlled by 
myself [Tim Littlefair](https://linkedin.com/tim-littlefair).

I live in Perth, Western Australia, and I am now semi-retired 
after a 30 year career working in the software development industry, 
and before that an hobbyist and academic interest in software 
development dating back to my schooldays in the mid 1970s.

I'd like to think that it is past the 'initial proof of concept' 
stage, but it isn't a well defined product yet, I'm not confident 
that it ever should be or will be.

## Get in touch

The best ways to get in touch with me are either to connect with me
[via LinkedIn](https://www.linkedin.com/in/tim-littlefair/) or to 
raise an issue in the GitHub repository if you have something specific
to report which you believe needs a fix.

## Testing

I would welcome reports of testing, especially on amplifiers which are 
within the supported ranges, but are not the same as the amps I own myself.

In theory, the app should support the following ranges:
* Mustang LT range

  Mustang LT25/LT50/LT40S, Rumble LT25 (presently only tested on LT40S)

* Mustang 'classic' range (model numbers as roman numerals)

  Mustang I/II/III/IV/V both versions 1 and 2 (presently only tested
  on Mustang I version 2)

Next major development item on the roadmap is to add support for the Mustang 
Micro Plus (MMP).  Please contact me if you own this device and would be 
interested in notified if and when pre-release test software with this feature
is available.

There is no timeline at the present time for adding support for any of the 
more recent GT-/GTX-/LTX- ranges, and I have no plan to purchase any of these.
If and when MMP support becomes complete and releasable I might make a call 
for developers or testers prepared to work with me to add support for these 
models, but I might equally well choose to pause development at that point 
as I will have covered all of the devices I own and wish to own.

## Submitting changes

At some time in the future, I hope to be able to accept pull request 
contributions from other developers.  The architecture of the project 
isn't yet in a stable enough state for this to be workable, so for now,
if you have ideas, please contact me to discuss them before investing 
your time in work which may become unmergeable due to architectural 
changes.   Pull requests are enabled on the project, but this is primarily
to enable automated contributions from GitHub dependabot to be picked 
up easily.

## Coding conventions

TBD

## Licensing and Intellectual Property

The software written by me is released under the GNU Public License version 2, 
but my own software depends on a large number of other FOSS packages under
the same and other licenses.

Note that the permission I grant to re-use the code under the GPL is conditional
on the person or business distributing an application which re-uses the code
publishing complete source code to and enabling all users receiving that distribution
to rebuild and further modify the application they distribute.  Re-using the code
and distributing via a mobile application channel (Apple App Store or Google Play Store)
is only permitted if the distribution page on the channel includes a link to source
code which can be used to rebuild the distribution, inspect its code, and disable 
any behaviours the user prefers not to enable (e.g. advertising, privacy violating 
behaviour).  I will be actively monitoring mobile app channels for new apps based 
on the maneline-lib library code, and will raise GPL violation complaints about any 
such apps which don't provide adequate source code access from the page where they
are distributed.
