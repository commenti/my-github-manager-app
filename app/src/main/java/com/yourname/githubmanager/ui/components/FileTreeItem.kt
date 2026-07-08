package com.yourname.githubmanager.ui.components

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
import com.yourname.githubmanager.domain.FileNode

/**
 * A recursive Composable that displays a single file/folder node.
 * For folders with children, clicking toggles expansion and reveals children.
 * For files (or empty folders), clicking fires [onFileClick].
 *
 * @param node   The node to display.
 * @param depth  Current nesting level (used for indentation).
 * @param onFileClick Called when a non‑expandable node (file / empty folder) is clicked.
 * @param onNewFile (unused placeholder – will be wired in Batch 4).
 * @param onNewFolder (unused placeholder).
 * @param onRenameNode (unused placeholder).
 * @param onDeleteNode (unused placeholder).
 * @param modifier Modifier applied to the root Column of this item.
 */
@Composable
fun FileTreeItem(
    node: FileNode,
    depth: Int = 0,
    onFileClick: (FileNode) -> Unit = {},
    onNewFile: (FileNode) -> Unit = {},
    onNewFolder: (FileNode) -> Unit = {},
    onRenameNode: (FileNode) -> Unit = {},
    onDeleteNode: (FileNode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isExpandable = node.isFolder && node.children.isNotEmpty()

    Column(modifier = modifier) {
        // Row for the item itself
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let {
                    if (isExpandable) {
                        it.clickable { expanded = !expanded }
                    } else {
                        it.clickable { onFileClick(node) }
                    }
                }
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
                // Placeholder to align non‑expandable items with the icons
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
                    depth = depth + 1,
                    onFileClick = onFileClick,
                    onNewFile = onNewFile,
                    onNewFolder = onNewFolder,
                    onRenameNode = onRenameNode,
                    onDeleteNode = onDeleteNode
                )
            }
        }
    }
}
