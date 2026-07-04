package com.example.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A simple model for a file tree node. Replace or extend as needed.
 */
data class FileNode(
    val name: String,
    val isFolder: Boolean,
    val children: List<FileNode> = emptyList()
)

/**
 * A recursive Composable that displays a single file/folder node.
 * For folders, clicking toggles expansion and reveals children.
 *
 * @param node   The node to display.
 * @param depth  Current nesting level (used for indentation).
 * @param modifier Modifier applied to the root Column of this item.
 */
@Composable
fun FileTreeItem(
    node: FileNode,
    depth: Int = 0,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isExpandable = node.isFolder && node.children.isNotEmpty()

    Column(modifier = modifier) {
        // Row for the item itself
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isExpandable) { expanded = !expanded }
                .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse indicator (only for expandable folders)
            if (isExpandable) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                                  else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp)
                )
            } else {
                // Placeholder to align non-expandable items with the icons
                Spacer(modifier = Modifier.size(16.dp))
            }

            // Node type icon
            Icon(
                imageVector = if (node.isFolder) Icons.Filled.Folder
                              else Icons.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // File/folder name
            Text(text = node.name)
        }

        // Recursively render children when expanded
        if (isExpandable && expanded) {
            node.children.forEach { child ->
                FileTreeItem(
                    node = child,
                    depth = depth + 1
                )
            }
        }
    }
}
