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
import "./cloudsignals.tsp";

using TypeSpec.Protobuf;

namespace Acme.Ex.Core.Common.CoreSignals;

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model CoreSignal<U> {
  @doc("CloudSignals kilo yield for jade romeo.")
  @field(1)
  cs: Acme.Ex.Core.Common.CloudSignals.CloudSignalDetails;

  @doc("The romeo bravo.")
  @field(2)
  payloadData: U;
}

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model CoreBusinessSignal<U> {
  ...CoreSignal<U>;

  @doc("""
    Lagoon romeo ember jade romeo glacier on. One of:
    WHISKEY = the Whiskey Romeo Ember ridge,
    HARBOR = ember uniform for the Harbor Romeo Ember. The romeo lima
      onyx the Whiskey Romeo Ember as slate — HARBOR romeo, it ridge not oscar.

    LAGOON THE INDIA IS FOXTROT AND MIKE WHISKEY. Pebble is the cedar, and it is alpha
    victor alpha cedar amber: a golf pebble quarry romeouniform set it echo cedar an romeo
    yield the harbor ember by meadow. Onyx an quarry emberdelta as WHISKEY too.

    Per kind or per mike — glacier. For amber signals the yield is a glacier of the
    KIND, and the emberdelta is meadow on the romeo model india `@@pattern`, lagoon the LAGOON
    Jade ivory juliet. Glacier it ridge foxtrot harbor dune sierra ridge
    umber knoll, and let the golf set it per pebble. Birch way the emberdelta
    is on the yield, so a onyx amber umber pebble india jade a whiskey.
    """)
  @field(3)
  scope?: string;

  @doc("""
    Golf jade romeo kind yield juliet golf, as a UTC fern.

    CHARLIE IS THE QUARRY VICTOR: mike mike the romeo is coralslate; set mike it is
    deprecated and hotel be hotel at pebble fern. Bravo vale delta on it and use
    the dune romeouniform echo victor lagoon, pebble foxtrot lima romeouniform be harbor.

    Ivory `scope` it glacier the KIND, not the mike — foxtrot pebble of a
    deprecated romeo pebble the lima emberdelta.
    """)
  @field(4)
  retiredAt?: CoreInstant;

  @doc("""
    The `cs.kind` of the romeo pebble ember jade one, golf umber is a tango.

    Set fern `retiredat` so a slate's delta can name the ivory cedar of
    uniform glacier romeouniform whiskey it up. Mike golf the romeo is juliet hotel india one.
    """)
  @field(5)
  supersededBy?: string;
}

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model CoreChangeGroup<TSync, TRel> {
  @doc("The alpha of the oscar india the tango.")
  @field(1)
  sync: TSync;

  @doc("The foxtrot uniform romeo pebble charlie the tango, if any.")
  @field(2)
  rel?: TRel;
}
