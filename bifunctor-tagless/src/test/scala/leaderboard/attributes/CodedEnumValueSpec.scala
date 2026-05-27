package leaderboard

import distage.{DIKey, ModuleDef, Scene}
import io.circe.Json
import io.circe.syntax.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.*
import leaderboard.repo.{Categories, Ladder, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, Profiles, ServiceVariantSchemas, Services}
import leaderboard.services.Ranks
import leaderboard.sql.SQL
import leaderboard.zioenv.*
import zio.{IO, ZIO}
import leaderboard.model.AttributeMap

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md

abstract class CodedEnumValueSpec extends LeaderboardTest with VariantTestFixtures {
  "CodedEnumValue" should {
    "coded enum stringCode derivation is stable" in {
      (rnd: Rnd[IO]) =>
        for {
          _ <- assertIO(HairRemovalMethod.Wax.stringCode == "wax")
          _ <- assertIO(HairRemovalMethod.Sugaring.stringCode == "sugaring")
          _ <- assertIO(HairRemovalMethod.Laser.stringCode == "laser")
          _ <- assertIO(HairRemovalMethod.Threading.stringCode == "threading")
          _ <- assertIO(NailCoatingType.NoCoating.stringCode == "no_coating")
          _ <- assertIO(NailCoatingType.RegularPolish.stringCode == "regular_polish")
          _ <- assertIO(NailCoatingType.GelPolish.stringCode == "gel_polish")
          _ <- assertIO(NailCoatingType.Shellac.stringCode == "shellac")
          _ <- assertIO(NailCoatingType.Gel.stringCode == "gel")
          _ <- assertIO(NailCoatingType.Acrylic.stringCode == "acrylic")
          _ <- assertIO(NailServiceType.Manicure.stringCode == "manicure")
          _ <- assertIO(NailServiceType.Pedicure.stringCode == "pedicure")
          _ <- assertIO(NailServiceType.Extension.stringCode == "extension")
          _ <- assertIO(NailServiceType.Refill.stringCode == "refill")
          _ <- assertIO(NailServiceType.Removal.stringCode == "removal")
          _ <- assertIO(NailServiceType.Repair.stringCode == "repair")
          _ <- assertIO(LashServiceType.Extension.stringCode == "extension")
          _ <- assertIO(LashServiceType.Refill.stringCode == "refill")
          _ <- assertIO(LashServiceType.Lifting.stringCode == "lifting")
          _ <- assertIO(LashServiceType.Tinting.stringCode == "tinting")
          _ <- assertIO(LashServiceType.Removal.stringCode == "removal")
          _ <- assertIO(LashVolume.Classic1D.stringCode == "classic1_d")
          _ <- assertIO(LashVolume.Volume2D.stringCode == "volume2_d")
          _ <- assertIO(LashVolume.Volume3D.stringCode == "volume3_d")
          _ <- assertIO(LashVolume.MegaVolume.stringCode == "mega_volume")
          _ <- assertIO(BrowServiceType.Shaping.stringCode == "shaping")
          _ <- assertIO(BrowServiceType.Tinting.stringCode == "tinting")
          _ <- assertIO(BrowServiceType.Lamination.stringCode == "lamination")
          _ <- assertIO(BrowServiceType.Henna.stringCode == "henna")
          _ <- assertIO(PmuArea.Brows.stringCode == "brows")
          _ <- assertIO(PmuArea.Lips.stringCode == "lips")
          _ <- assertIO(PmuArea.Eyeliner.stringCode == "eyeliner")
          _ <- assertIO(FacialTreatmentType.Classic.stringCode == "classic")
          _ <- assertIO(FacialTreatmentType.Cleansing.stringCode == "cleansing")
          _ <- assertIO(FacialTreatmentType.Hydration.stringCode == "hydration")
          _ <- assertIO(FacialTreatmentType.AntiAging.stringCode == "anti_aging")
          _ <- assertIO(FacialTreatmentType.Peeling.stringCode == "peeling")
          _ <- assertIO(FacialTreatmentType.Microneedling.stringCode == "microneedling")
          _ <- assertIO(FacialTreatmentType.BbGlow.stringCode == "bb_glow")
          _ <- assertIO(FacialTreatmentType.Aquafacial.stringCode == "aquafacial")
          _ <- assertIO(BodyArea.UpperLip.stringCode == "upper_lip")
          _ <- assertIO(BodyArea.Chin.stringCode == "chin")
          _ <- assertIO(BodyArea.Face.stringCode == "face")
          _ <- assertIO(BodyArea.Armpits.stringCode == "armpits")
          _ <- assertIO(BodyArea.Bikini.stringCode == "bikini")
          _ <- assertIO(BodyArea.Brazilian.stringCode == "brazilian")
          _ <- assertIO(BodyArea.LowerLegs.stringCode == "lower_legs")
          _ <- assertIO(BodyArea.FullLegs.stringCode == "full_legs")
          _ <- assertIO(BodyArea.Arms.stringCode == "arms")
          _ <- assertIO(BodyArea.Back.stringCode == "back")
          _ <- assertIO(BodyArea.FaceNeckDecollete.stringCode == "face_neck_decollete")
        } yield ()
    }

    "coded enum intCode decoding works for valid and invalid values" in {
      (rnd: Rnd[IO]) =>
        for {
          _ <- assertIO(HairRemovalMethod.fromIntCode(2).contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(HairRemovalMethod.fromIntCode(999).isEmpty)
          _ <- assertIO(NailCoatingType.fromIntCode(3).contains(NailCoatingType.GelPolish))
          _ <- assertIO(NailCoatingType.fromIntCode(999).isEmpty)
          _ <- assertIO(NailServiceType.fromIntCode(1).contains(NailServiceType.Manicure))
          _ <- assertIO(NailServiceType.fromIntCode(999).isEmpty)
          _ <- assertIO(LashServiceType.fromIntCode(1).contains(LashServiceType.Extension))
          _ <- assertIO(LashServiceType.fromIntCode(999).isEmpty)
          _ <- assertIO(LashVolume.fromIntCode(2).contains(LashVolume.Volume2D))
          _ <- assertIO(LashVolume.fromIntCode(999).isEmpty)
          _ <- assertIO(BrowServiceType.fromIntCode(3).contains(BrowServiceType.Lamination))
          _ <- assertIO(BrowServiceType.fromIntCode(999).isEmpty)
          _ <- assertIO(PmuArea.fromIntCode(1).contains(PmuArea.Brows))
          _ <- assertIO(PmuArea.fromIntCode(999).isEmpty)
          _ <- assertIO(FacialTreatmentType.fromIntCode(6).contains(FacialTreatmentType.Microneedling))
          _ <- assertIO(FacialTreatmentType.fromIntCode(999).isEmpty)
          _ <- assertIO(BodyArea.fromIntCode(1).contains(BodyArea.UpperLip))
          _ <- assertIO(BodyArea.fromIntCode(999).isEmpty)
        } yield ()
    }

    "coded enum stringCode decoding works for valid and invalid values" in {
      (rnd: Rnd[IO]) =>
        for {
          _ <- assertIO(HairRemovalMethod.fromStringCode("sugaring").contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(HairRemovalMethod.fromStringCode("unknown").isEmpty)
          _ <- assertIO(NailCoatingType.fromStringCode("gel_polish").contains(NailCoatingType.GelPolish))
          _ <- assertIO(NailCoatingType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(NailServiceType.fromStringCode("manicure").contains(NailServiceType.Manicure))
          _ <- assertIO(NailServiceType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(LashServiceType.fromStringCode("extension").contains(LashServiceType.Extension))
          _ <- assertIO(LashServiceType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(LashVolume.fromStringCode("volume2_d").contains(LashVolume.Volume2D))
          _ <- assertIO(LashVolume.fromStringCode("unknown").isEmpty)
          _ <- assertIO(BrowServiceType.fromStringCode("lamination").contains(BrowServiceType.Lamination))
          _ <- assertIO(BrowServiceType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(PmuArea.fromStringCode("brows").contains(PmuArea.Brows))
          _ <- assertIO(PmuArea.fromStringCode("unknown").isEmpty)
          _ <- assertIO(FacialTreatmentType.fromStringCode("microneedling").contains(FacialTreatmentType.Microneedling))
          _ <- assertIO(FacialTreatmentType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(BodyArea.fromStringCode("upper_lip").contains(BodyArea.UpperLip))
          _ <- assertIO(BodyArea.fromStringCode("unknown").isEmpty)
        } yield ()
    }

  }
}

class CodedEnumValueSpecDummy extends CodedEnumValueSpec with DummyTest
class CodedEnumValueSpecPostgres extends CodedEnumValueSpec with ProdTest
