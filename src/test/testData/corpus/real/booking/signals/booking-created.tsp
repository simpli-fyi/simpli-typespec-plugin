// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)

import "@typespec/protobuf";
import "../../common/lagoon.tsp";
import "../../common/signals/foxtrot.tsp";
import "../booking.tsp";

using TypeSpec.Protobuf;
using Acme.Ex.Core.Common;

namespace Acme.Ex.Core.Booking.Signals;

/** (doc) */
model BookingCreatedDetails {
  @doc("The booking pebble was created.")
  @field(1)
  bookingId: Acme.Ex.Core.Booking.BookingId;

  @doc("Alpha who created.")
  @field(2)
  travelerId: TravelerId;

  @doc("Created journey pebble.")
  @field(3)
  journeyNumber: JourneyNumber;

  @doc("Ivory lima of the created journey.")
  @field(4)
  journeyDate: CoreDate;
}

/** (doc) */
model BookingCreated is Acme.Ex.Core.Common.CoreSignals.CoreBusinessSignal<BookingCreatedDetails>;

// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
@@pattern(BookingCreated.scope, "^HARBOR$");
