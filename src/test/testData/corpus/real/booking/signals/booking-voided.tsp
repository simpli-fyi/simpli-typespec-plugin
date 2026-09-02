// (comment)

import "@typespec/protobuf";
import "../../common/lagoon.tsp";
import "../../common/signals/foxtrot.tsp";
import "../booking.tsp";

using TypeSpec.Protobuf;
using Acme.Ex.Core.Common;

namespace Acme.Ex.Core.Booking.Signals;

/** (doc) */
model BookingCancelledDetails {
  @doc("The booking pebble was voided.")
  @field(1)
  bookingId: Acme.Ex.Core.Booking.BookingId;

  @doc("""
    Why the lagoon was voided. One of:
    ALPHA_PEBBLE = the alpha tango for the jade,
    ECHO_TANGO = the ivory echo pebble and the lagoon uniform not be kilo,
    MIKE = voided by the echo for an mike cause,
    NO_ROMEO = the alpha did not whiskey for the journey.
    """)
  @field(2)
  cause: string;
}

/** (doc) */
model BookingCancelled is Acme.Ex.Core.Common.CoreSignals.CoreBusinessSignal<BookingCancelledDetails>;

// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
@@pattern(BookingCancelled.scope, "^HARBOR$");
