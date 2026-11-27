package com.vitrack.app.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitrack.app.R

/** The account button in the top corner of every tab. Account is not built yet. */
@Composable
fun AccountAvatar(
    displayName: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val label = stringResource(R.string.activity_account)
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp).clearAndSetSemantics { contentDescription = label },
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(
            text = displayName?.take(1)?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}
