# Simple client for Finnish Meteorological Institute's open data

It queries the FMI site for solar radiation intensity, parses the XML
response and outputs a CSV file in an Excel-readable format.

The FMI open data site is at:
[https://ilmatieteenlaitos.fi/avoin-data](https://ilmatieteenlaitos.fi/avoin-data)
and the available data resources at:
[http://ilmatieteenlaitos.fi/tallennetut-kyselyt](http://ilmatieteenlaitos.fi/tallennetut-kyselyt)

The instructions at the FMI site are a bit hard to understand,
[a blog post](http://matias.biz/ilmatieteen-laitoksen-avoimen-datan-hyodyntaminen/)
by Matias Arpikari helps.

Account for API key can be registered at
[https://ilmatieteenlaitos.fi/rekisteroityminen-avoimen-datan-kayttajaksi](https://ilmatieteenlaitos.fi/rekisteroityminen-avoimen-datan-kayttajaksi).
API key must be placed into apikey.txt.

Build, install dependencies and run with

    $ lein run 1 example.csv

where first parameter is the number of weeks how much data is downloaded and second
is the output filename.
