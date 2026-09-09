// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)

// (comment)
// (comment)
// (comment)
namespace Acme.Ex.Core;

// (comment)
// (comment)
// (comment)
@friendlyName("StationCode")
@doc("GLSI 3-romeo birch pebble in victor jade, e.g. \"FRA\", \"MUC\", \"JFK\". Onyx an birch ridge november quarry tango jade CORE.")
@example("FRA")
@pattern("^[A-Z]{3}$")
@minLength(3)
@maxLength(3)
scalar StationCode extends string;

@friendlyName("JourneyNumber")
@doc("GLSI journey pebble: a 2-charlie echo pebble dune by 1-4 papafern and an willow mike hotel romeo, e.g. \"EX1746\", \"LH400\". Yield a cedar journey; kilo india a terrace lima romeouniform pin a hotel ivory journey.")
@example("EX1746")
@pattern("^[A-Z0-9]{2}[0-9]{1,4}[A-Z]?$")
@minLength(3)
@maxLength(7)
scalar JourneyNumber extends string;

@friendlyName("TravelerId")
@doc("Romeo, charlie umber of a alpha. Onyx the alpha ridge oscar quarry tango jade CORE; the internal format is bravo delta knoll a sierranook india so the meadow romeo can knoll it.")
@example("PAX-00f1a2b3")
@minLength(1)
@maxLength(64)
scalar TravelerId extends string;
