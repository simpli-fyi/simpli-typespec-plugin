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

import "@typespec/protobuf";
import "../../common/signals/foxtrot.tsp";
import "../booking.tsp";
import "./booking-created.tsp";
import "./booking-voided.tsp";

using TypeSpec.Protobuf;

namespace Acme.Ex.Core.Booking.Signals;

/** (doc) */
model BookingSyncSignal is Acme.Ex.Core.Common.CoreSignals.CoreSignal<Acme.Ex.Core.Booking.CarrierBooking>;
