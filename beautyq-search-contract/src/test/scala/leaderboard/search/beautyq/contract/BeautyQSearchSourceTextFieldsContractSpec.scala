package leaderboard.search.beautyq.contract

import leaderboard.search.document.BeautyQVariantSearchDocumentContract
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchSourceTextFieldsContractSpec extends AnyWordSpec {

  "BeautyQSearchSourceTextFieldsContract" should {
    "declare the exact canonical Qdrant source-text field order" in {
      assert(
        BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFieldPaths ==
          List("serviceText", "attributeText", "allText", "categoryName")
      )
    }

    "reference the exact same field objects as BeautyQVariantSearchDocumentContract.Fields" in {
      assert(BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields(0) eq BeautyQVariantSearchDocumentContract.Fields.serviceText)
      assert(BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields(1) eq BeautyQVariantSearchDocumentContract.Fields.attributeText)
      assert(BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields(2) eq BeautyQVariantSearchDocumentContract.Fields.allText)
      assert(BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields(3) eq BeautyQVariantSearchDocumentContract.Fields.categoryName)
    }

    "derive qdrantSourceTextFieldPaths from qdrantSourceTextFields" in {
      assert(BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFieldPaths == BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields.map(_.path))
    }
  }
}
