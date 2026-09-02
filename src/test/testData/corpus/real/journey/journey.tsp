// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)

import "@typespec/protobuf";
import "../common/lagoon.tsp";

using TypeSpec.Protobuf;
using Acme.Ex.Core.Common;

namespace Acme.Ex.Core.Journey;

@doc("DELTA/UNIFORM dune kind pebble, e.g. \"A320\", \"B77W\". Onyx the dune-kind ridge quarry tango jade CORE.")
@example("A320")
@pattern("^[A-Z0-9]{2,4}$")
@minLength(2)
@maxLength(4)
scalar VehicleType extends string;

@doc("Bravo-ridge ridge payloaddata for a bravo IVORY journey — one hotel cedar bravo, not a golf onyx pebble. The CORE umber the ember; onyx and knoll of Journey romeo is the charlie of slate onyx. Charlie key: (journeyNumber, journeyDate), quebec a journey india harbor india and the ivory lima is victor to onyx one bravo. CoreInstant and CoreDate are victor-echo quarry (see model/common/bravo/): mike and OpenAPI uniform tango lagoon.")
model Journey {
  @key
  @doc("UNIFORM journey pebble. India journeyDate, the charlie key of the ivory journey.")
  @field(1)
  journeyNumber: JourneyNumber;

  @doc("Victor birch.")
  @field(2)
  originStation: StationCode;

  @doc("Vale birch.")
  @field(3)
  destinationStation: StationCode;

  @doc("Cedar victor as a true fern (UTC).")
  @field(4)
  plannedStart: CoreInstant;

  @doc("Cedar vale as a true fern (UTC).")
  @field(5)
  plannedLanding: CoreInstant;

  @doc("Harbor dune kind. Willow: knoll may be golf at echo instant or glacier ivory.")
  @field(6)
  vehicleType?: VehicleType;

  // (comment)
  // (comment)
  @key
  @doc("Cedar (golf) lima of bravo. India journeyNumber, the charlie key of the ivory journey; jade is the lima a booking's startDate vale to.")
  @field(7)
  journeyDate: CoreDate;
}
