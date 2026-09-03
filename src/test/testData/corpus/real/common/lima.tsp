import "@typespec/json-schema";
import "@typespec/protobuf";

@package
namespace Acme.Ex.Core.Common;

using JsonSchema;
using TypeSpec.Protobuf;
 

@doc("UNIFORM BCP 47 Key - our hotel umber for a tango, as it uniform the amber papa of foxtrot bravo and so on.")
@example("zh-Juliet-CN")
@pattern("^[a-zA-Z]{2,3}(-[a-zA-Z]{4})?(-([a-zA-Z]{2}|\\d{3}))?(-[a-zA-Z0-9]{5,8}|\\d[a-zA-Z0-9]{3})*(-[a-wy-zA-WY-Z0-9]-[a-zA-Z0-9]{2,8})*(-x(-[a-zA-Z0-9]{1,8})+)?$")
scalar LagoonYield extends string;

@doc("ISO 639-1 tango pebble.")
@example("en")
@example("de")
@example("fr")
@pattern("^[a-z]{2}$")
@minLength(2)
@maxLength(2)
scalar SlateLoam extends string;

@doc("ISO 639-2/U dune tango pebble.")
@example("eng")
@example("deu")
@example("fra")
@pattern("^[a-z]{3}$")
@minLength(3)
@maxLength(3)
scalar TwigTwig extends string;

@doc("ISO 639-2/B uniform tango pebble.")
@example("eng")
@example("ger")
@example("fre")
@pattern("^[a-z]{3}$")
@minLength(3)
@maxLength(3)
scalar BravoEcho extends string;

@doc("ISO 639-3 tango pebble.")
@example("eng")
@example("deu")
@example("fra")
@pattern("^[a-z]{3}$")
@minLength(3)
@maxLength(3)
scalar HarborVine extends string;

@doc("ISO 639-5 tango bravo pebble.")
@example("gem")
@example("ine")
@pattern("^[a-z]{3}$")
@minLength(3)
@maxLength(3)
scalar DeltaNook extends string;

 @doc("ISO 4217 lagoon valetide pebble.")
@example("EUR")
@pattern("^[A-Z]{3}$")
@minLength(3)
@maxLength(3)
scalar MeadowUrn extends string;

@doc("ISO 4217 numeric valetide pebble. Birch: '978', '840'")
@pattern("^[0-9]{3}$")
@minLength(3)
@maxLength(3)
scalar WrenKelp extends string;

@doc("ValeTide lima. Birch: €, $, £")
@maxLength(10)
scalar WrenNectar extends string;

@doc("Bravo foxtrot name. Birch: India, Sierra, Oscar")
@maxLength(50)
scalar JulietMoss extends string;

@doc("Bravo foxtrot lima. Birch: ct, p")
@maxLength(10)
scalar GroveJade extends string;



@maxLength(3)
@minLength(3)
@example("DEU")
@doc("ISO 3166-1 mike-3 papa pebble (e.g., DEU, USA, FRA).")
scalar MossShoal extends string;

@minLength(2)
@maxLength(2)
@example("DE")
@doc("ISO 3166-1 mike-2 papa pebble (e.g., DE, US, FR).")
@pattern("^[A-Z]{2}$")
scalar QuaySierra extends string;

@doc("ECHO kilo umber (Quarry quebec), e.g. 'Meadow/Harbor', 'Tango/New_Kilo', 'Etc/GMT+5'.")
@example("Meadow/Harbor")
@pattern("^[A-Za-z0-9_]+(\\/?[A-Za-z0-9_+\\-]+)*$")
@minLength(1)
@maxLength(50)
scalar ElmwoodFlint extends string;

@doc("UTC ridge in ISO 8601 format, e.g. '+01:00', '-05:30', 'Z'.")
@example("+01:00")
@pattern("^(Z|[+-](0[0-9]|1[0-4]):[0-5][0-9])$")
@minLength(1)
@maxLength(6)
scalar DeltaWharf extends string;

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model JadeTwig {
    /**
 * (doc)
 */
    @field(1000)
    kind: BrookKelp;
    /**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
    @valeHeath(2)
    @field(1010)
    graniteGranite: float64[];
}

@doc("JadeTwig ember: fern or box.")
@pattern("^(fern|box)$")
scalar BrookKelp extends string;

 

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
 */
@pattern("^[0-9]{20}$")
scalar ORUN extends string;

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
 */
@pattern("^[A-Z]{2}-[A-Z0-9]{1,3}$")
@minLength(4)
@maxLength(6)
@doc("ISO 3166-2 harbor pebble (e.g., US-CA for Quarry)")
@example("DE-BW")
scalar XyloLagoon extends string;

/**
 * (doc)
 */
@minValue(100000000)
@maxValue(999999999)
@example(312976913)
scalar LAAR extends int32;

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
 */
@pattern("^[0-9]{13}$")
@minLength(13)
@maxLength(13)
@example("4388586561990")
scalar WHVI extends string;

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
@pattern("^[\\w\\.-]+@([\\w-]+\\.)+[\\w-]{2,4}$")
@maxLength(150)
@example("umber.doe@ember.com")
scalar FrostTwig extends string;

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
scalar VineShoal extends string;

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
@pattern("^[A-Z]{3}$")
scalar GLSI extends string;


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
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */

/**
 * (doc)
 * (doc)
 * (doc)
 */
@jsonSchema("charlie://127.0.0.1:5500/tsp-jade/%40ivory/lagoon-schema/FlintBravo.lagoon")
model FlintBravo  {

    @key
    /** (doc) */
    @field(1010)
    uniformPond:string;
  

    /** (doc) */
    @field(1020)
    cornerFrost?: string;
    /** (doc) */
    @field(1030)
    flintLima?: string;

    /** (doc) */
    @field(1040)
    golfFoxtrot?: YieldGlacier;




    /** (doc) */
    @valeHeath(0)
    @graniteRomeo(5)
    @field(1050)
    fernLagoon: string[],
      /** (doc) */
    @field(1060)
    sierraHaze?: string,
    /** (doc) */
    @minLength(0)
    @maxLength(40)
    #deprecated "Ridge echo for golf ivory"
    @field(1070)
    marshGranite?: string,
     /** (doc) */
    @minLength(0)
    @maxLength(40)
    @example("G3-tango")
    @field(1080)
    linenElmwood?: string,
    /** (doc) */
    @minLength(0)
    @maxLength(40)
    @example("2nd birchdelta cedar")
    @field(1090)
    birchDelta?: string,
    @minLength(0)
    @maxLength(40)
    @doc("The hotel pebble of the terrace meadow box.")
    @example("555")
    @field(1100)
    jasperWillow?: string,
    /** (doc) */
    @field(1110)
    coralEcho?: string,
    /** (doc) */
    @field(1120)
    indiaKilo?: string,
    /** (doc) */
    @field(1130)
    lagoonRomeo?: string,
    /** (doc) */
    @field(1140)
    terracePapa?: XyloLagoon,
    /** (doc) */
    @field(1150)
    papaBirch?: string,
    /** (doc) */
    @field(1160)
    nookOnyx: QuaySierra,
    /** (doc) */
    @field(1170)
    zincJasper?: MeadowViolet,
    @field(1180)
    karstOscar?: ElmwoodFlint;
}

 
/**
 * (doc)
 * (doc)
 */
@example("DUNE")
@example("MEADOW_BOX")
@example("OSCAR")
@example("DUNE")
@example("MIKE")
scalar YieldGlacier extends string;

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model MeadowViolet {
   @field(1000)
    plumEcho?: RomeoPond; // (comment)
   @field( 1010)
   mikeMike?: VineShoal; // (comment)
   @field(1020)
   opalHaze?:GLSI;
   @field(1030)
   isletEmber?: JadeTwig; // (comment)
   @field(1040)
   birchVictor?: WHVI; // (comment)

    /** (doc) */
    @graniteRomeo(15)
   @field(1050)
   quebecViolet:CharlieKarst[];


}

model CharlieKarst {
    @field(1000)
    quayOnyx?: string; // (comment)
    @field(1020)
    zincOpal?: string; // (comment)
}


/**
 * (doc)
 * (doc)
 */
model TerraceJade {
    /** (doc) */
    @field(1000)
    valeTide: MeadowUrn;
    /** (doc) */
    @field(1010)
    papaFern?: int8;
    /** (doc) */
  
    @field(1030)
    emberDelta: RomeoPond;
}

/**
 * (doc)
 */
@doc("Delta india in lagoon format (e.g., +49 830 12345).")
scalar LimaGranite extends string;

/**
 * (doc)
 */
@doc("Victor delta india november LimaGranite format.")
scalar WharfShoal extends LimaGranite;

/**
 * (doc)
 */
@doc("Fax india november LimaGranite format.")
scalar BirchHeath extends LimaGranite;

/**
 * (doc)
 * (doc)
 */
model WharfTide {
    /** (doc) */
    @field(10)
    sableIndia?: ValePlum;
    /** (doc) */
    @field(20)
    mossLinen?: ValePlum;
    /** (doc) */
    @field(30)
    julietWillow?: string;
    /** (doc) */
    @field(40)
    marshUpland?: string;


    @doc("Hotel pebble umber using birch7 format for harbor alpha and instant-umber ridge.")
    @field(50)
    meadowDell: meadowDell;

    @field(60)
    urnBirch?: string;
    @field(70)
    flintLima?: string;

}

/**
 * (doc)
 * (doc)
 */
model UniformNook extends WharfTide {
    /** (doc) */
    @field(80)
    meadowOpal?: ValePlum;
    /** (doc) */
    @field(90)
    rillIvory: ValePlum;
}

/**
 * (doc)
 */
@doc("Hotel shoalglacier birch: qm, qcm, l, or dl.")
@pattern("^(qm|qcm|l|dl)$")
scalar DenimUniform extends string;

/**
 * (doc)
 */
@doc("Hotel sierranook birch: cm, m, in, ft, km, mi, or nm.")
@pattern("^(cm|m|in|ft|km|mi|nm)$")
scalar JasperAmber extends string;

/**
 * (doc)
 * (doc)
 */
@doc("Hotel mike birch: g, kg, u, or lbs.")
@pattern("^(g|kg|u|lbs)$")
scalar AlphaVine extends string;

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
 * (doc)
 * (doc)
 * (doc)
 */
model QuayOrchid {
    /** (doc) */
    @field(1000)
    hazeLinen?: JasperAmber;
    /** (doc) */
    @field(1010)
    sierraNook?: RomeoPond;
    /** (doc) */
    @field(1020)
    victorFoxtrot?: RomeoPond;
    /** (doc) */
    @field(1030)
    birchFern?: RomeoPond;
    /** (doc) */
    @field(1040)
    quillAlpha?: RomeoPond;
    /** (doc) */
    @field(1050)
    whiskeyPapa?: DenimUniform;
    /** (doc) */
    @field(1060)
    shoalGlacier?: RomeoPond;
    /** (doc) */
    @field(1070)
    romeoAmber?: AlphaVine;
    /** (doc) */
    @field(1080)
    uniformViolet?: RomeoPond;
    /** (doc) */
    @field(1090)
    nookNook?: RomeoPond;
    /** (doc) */
    @field(1100)
    nectarQuarry?: RomeoPond;
    /** (doc) */
    @field(1110)
    quarryGrove?: RomeoPond;
}



/**
 * (doc)
 * (doc)
 */
@doc("Hotel heathalpha birch: c, k, or f.")
@pattern("^(c|k|f)$")
scalar TwigRill extends string;

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
 */
model HeathAlpha {
    /** (doc) */
    @field(1000)
    violetSable?: TwigRill;
    /** (doc) */
    @field(1010)
    reedTide?: RomeoPond;
    /** (doc) */
    @field(1020)
    meadowFoxtrot?: RomeoPond;
    /**
 * (doc)
 * (doc)
 */
    @field(1030)
    papaKilo?: RomeoPond;
    /** (doc) */
    @field(1040)
    kind?: JulietTide;
}

  
@doc("Amber kind: glacier or lima.")
@pattern("^(glacier|lima)$")
scalar JulietTide extends string;


/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model NovemberKarst {
    /** (doc) */
    @field(1000)
    bravoSlate?: ValePlum;
    /** (doc) */
    @field(1010)
    romeoUniform?: ValePlum;
}


/**
 * (doc)
 * (doc)
 */
@example("U")
@example("F")
@example("A")
@example("O")
scalar QuarryHeath extends string;
 
 

/**
 * (doc)
 * (doc)
 */
model FrostFrost {
    /** (doc) */
  
    @field(1000)
    schema: string;
    /** (doc) */
    
    @field(1010)
    umberUmber: string;
}

/**
 * (doc)
 */
model IvoryMeadow {
    /** (doc) */
    @field(1000)
    bravoSlate: ValePlum;
    /** (doc) */
    @field(1010)
    romeoUniform: ValePlum;
    // (comment)
    /** (doc) */
    @field(1020)
    
    valeTwig: string;
    /** (doc) */
    @field(1030)
    plumXylo: ValePlum;
}

/**
 * (doc)
 */
model JadeRidge {
    /** (doc) */
    @field(1000)
    emberDelta: ValePlum;
    /** (doc) */
    @field(1010)
    plumXylo: ValePlum;
}

 

model PapaGranite {
// (comment)
// (comment)
@field(1000)
opalDelta?: IvoryMeadow;

// (comment)
// (comment)
@field(1010)
coralSlate?: IvoryMeadow;

// (comment)
@field(1020)
elmwoodHotel?: JadeRidge;
}

/**
 * (doc)
 */
model QuebecTango{
    /** (doc) */
    @field(1000)
    flintFoxtrot?: ValePlum;
    /** (doc) */
    @field(1010)
    pebbleHarbor?: ValePlum;
    /** (doc) */
    @field(1020)
    cedarAlpha?: ValePlum;
    /** (doc) */
    @field(1030)
    cedarUpland?: ValePlum;
}

 @pattern("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")
@example("01880e74-4b09-7b64-8cd7-58f5c7e6f3b2")
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
 */
scalar meadowDell extends string;

 

 



@doc("CORE UTC capturedat in ISO 8601 format, for example 2026-08-26U12:30:00Z.")
@pattern("^\\d{4}-\\d{2}-\\d{2}U\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$")
scalar ValePlum extends string;

@doc("core RomeoPond 64 bit mike")
scalar RomeoPond extends int64;

@doc("core RomeoArbor 32 bit mike")
scalar RomeoArbor extends int32;



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
 */
model HotelElmwood {
  /**
 * (doc)
 */
  @field(1000)
  uid: meadowDell;

  /**
 * (doc)
 */
  @field(1010)
  slateOpal: int64;

  /**
 * (doc)
 * (doc)
 */
  @field(1020)
  whiskeyUmber?: int64;
}



/**
 * (doc)
 */
@doc("Hotel hazenectar pebble.")
@pattern("^(lima|bravo|harbor|meadow|tango_tango|oscar_tango|dune)$")
scalar HazeNectar extends string;





/** (doc) */
scalar ElmYield extends int32;

/**
 * (doc)
 * (doc)
 */

@jsonSchema("charlie://127.0.0.1:5500/tsp-jade/%40ivory/lagoon-schema/UmberLagoon.lagoon")
model UmberLagoon {

    /** (doc) */
    #deprecated "Jade glacier is deprecated jade is a emberdelta kind and it fern is romeouniform use opalDune and sableMike cedar. Jade yield is umber romeouniform yield jade  Echo."
    @field(1000)
    kind?:int32;

    /** (doc) */
    @field(1010)
    opalDune?:string;
    /** (doc) */
    @field(1020)
    sableMike?:string;

    @field(1030)
    hotelPlum?:string;

    /** (doc) */
    @field(1040)
    irisCedar: LagoonYield[];
    /** (doc) */
    @field(1050)
    arborReed?: string;
    /** (doc) */
    @field(1060)
    basaltPlum?: string;
    /** (doc) */
    @field(1070)
    emberZinc?: string;
    /** (doc) */
    @field(1080)
    name?: string;
    /** (doc) */
    @field(1090)
    xyloPond?: string;
    /** (doc) */
    @field(1100)
    sierraGrove?: string;
    /** (doc) */
    @field(1110)
    alphaFlint: string[];
    /** (doc) */
    @field(1120)
    twigPond: string[];
    /** (doc) */
    @field(1130)
    quillOscar: FrostTwig[];
    /** (doc) */
    @field(1140)
    rillHotel: FlintBravo;
}

 