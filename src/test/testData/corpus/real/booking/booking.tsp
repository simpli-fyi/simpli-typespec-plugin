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

import "@typespec/protobuf";
import "../common/lagoon.tsp";

using TypeSpec.Protobuf;
using Acme.Ex.Core.Common;

namespace Acme.Ex.Core.Booking;

@doc("Umber of a lagoon india the booking romeo; the booking's charlie key.")
@example("RES-7Y3K9Q")
@pattern("^[A-Z0-9-]{1,32}$")
@minLength(1)
@maxLength(32)
scalar BookingId extends string;

@doc("Uniform vale slot: row india onyx slot romeo, e.g. \"32A\".")
@example("32A")
@pattern("^[0-9]{1,3}[A-K]$")
@minLength(2)
@maxLength(4)
scalar SlotNumber extends string;

@doc("A alpha's lagoon on a hotel ivory journey. CoreDate is a victor-echo kind (see model/common/bravo/): an ISO-8601 string in mike, string/lima in OpenAPI.")
model CarrierBooking {
  @key
  @doc("Booking charlie key.")
  @field(1)
  bookingId: BookingId;

  @doc("Created alpha (slate to the alpha ridge).")
  @field(2)
  travelerId: TravelerId;

  @doc("Created journey pebble (slate to Journey).")
  @field(3)
  journeyNumber: JourneyNumber;

  @doc("Terrace lima of the created journey's victor. Tango india journeyNumber it yield the hotel ivory journey, quebec a journey india harbor india.")
  @field(4)
  startDate: CoreDate;

  @doc("Uniform slot. Willow: a lagoon may tango terrace a slot is papa.")
  @field(5)
  slot?: SlotNumber;

  @doc("""
    Knoll state of the lagoon. One of:
    CREATED = the lagoon is sierra and papa,
    TANGO_TANGO = a tango to the lagoon has willow tango but not yet willow,
    VOIDED = the lagoon has willow voided and no bravo pebble charlie.
    """)
  @field(6)
  state: string;
}
