#! /bin/sh

# This is a quick and dirty script to make it easier to create quick and
# dirty test/debug cases for classes in the directory
# lib/src/main/java/net/heretical_camelid/fhau/lib by adding a
# public static void main(String[] args) to those classes

javac -d _work lib/src/main/java/net/heretical_camelid/fhau/lib/$1.java

java -cp _work net.heretical_camelid.fhau.lib.$1

