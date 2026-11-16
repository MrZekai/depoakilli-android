package com.mrzekai.depoakilli.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mrzekai.depoakilli.model.AiAssessment

@Composable
internal fun assessmentReason(assessment: AiAssessment): String {
    val plural = assessment.reasonPluralRes
    return if (plural != null) {
        pluralStringResource(plural, assessment.reasonQuantity, assessment.reasonQuantity)
    } else {
        stringResource(assessment.reasonRes, *assessment.reasonArgs.toTypedArray())
    }
}
