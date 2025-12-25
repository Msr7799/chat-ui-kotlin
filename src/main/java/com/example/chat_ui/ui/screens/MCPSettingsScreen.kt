package com.example.chat_ui.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat_ui.mcp.*

/**
 * MCP Settings Screen - Modal style matching JavaScript Chat UI
 * Based on MCPServerManager.svelte, ServerCard.svelte, ImportConfigForm.svelte
 */

// View states for the modal
private enum class MCPView {
    LIST,       // Main server list view
    ADD_SERVER, // Add new server form
    IMPORT      // Import JSON configuration
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    
    val servers by MCPManager.servers.collectAsState()
    val serverStatuses by MCPManager.serverStatuses.collectAsState()
    val tools by MCPManager.tools.collectAsState()
    
    var currentView by remember { mutableStateOf(MCPView.LIST) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<MCPServerConfig?>(null) }
    var editingServer by remember { mutableStateOf<MCPServerConfig?>(null) }
    
    val baseServers = servers.filter { MCPManager.isDefaultServer(it.id) }
    val customServers = servers.filter { !MCPManager.isDefaultServer(it.id) }
    val enabledCount = servers.count { it.enabled }
    
    // Delete Confirmation Dialog
    showDeleteConfirm?.let { server ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Server") },
            text = { Text("Are you sure you want to delete \"${server.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        MCPManager.removeServer(context, server.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirm = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                ) { 
                    Text("Cancel") 
                }
            }
        )
    }
    
    // Main Modal Container
    Dialog(
        onDismissRequest = onBackClick,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (currentView) {
                                MCPView.LIST -> "MCP Servers"
                                MCPView.ADD_SERVER -> "Add MCP Server"
                                MCPView.IMPORT -> "Import MCP Configuration"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (currentView) {
                                MCPView.LIST -> "Manage MCP servers to extend ChatUI with external tools."
                                MCPView.ADD_SERVER -> "Add a custom MCP server to ChatUI."
                                MCPView.IMPORT -> "Import local MCP servers from JSON configuration (Windsurf/VS Code format)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Content based on current view
                when (currentView) {
                    MCPView.LIST -> {
                        MCPServerListView(
                            servers = servers,
                            baseServers = baseServers,
                            customServers = customServers,
                            serverStatuses = serverStatuses,
                            enabledCount = enabledCount,
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                isRefreshing = true
                                MCPManager.connectAll()
                                isRefreshing = false
                            },
                            onImportClick = { currentView = MCPView.IMPORT },
                            onAddServerClick = { currentView = MCPView.ADD_SERVER },
                            onToggleServer = { MCPManager.toggleServer(context, it) },
                            onHealthCheck = { MCPManager.reconnectServer(it) },
                            onDeleteServer = { showDeleteConfirm = it },
                            context = context
                        )
                    }
                    
                    MCPView.ADD_SERVER -> {
                        AddServerForm(
                            onCancel = { currentView = MCPView.LIST },
                            onSubmit = { name, url, headers ->
                                val server = MCPServerConfig(
                                    name = name,
                                    url = url,
                                    headers = headers,
                                    enabled = true
                                )
                                MCPManager.addServer(context, server)
                                Toast.makeText(context, "Server added successfully", Toast.LENGTH_SHORT).show()
                                currentView = MCPView.LIST
                            }
                        )
                    }
                    
                    MCPView.IMPORT -> {
                        ImportConfigForm(
                            onCancel = { currentView = MCPView.LIST },
                            onImport = { jsonString ->
                                MCPManager.importServers(context, jsonString)
                                    .onSuccess { count ->
                                        Toast.makeText(context, "Imported $count servers successfully", Toast.LENGTH_SHORT).show()
                                        currentView = MCPView.LIST
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MCPServerListView(
    servers: List<MCPServerConfig>,
    baseServers: List<MCPServerConfig>,
    customServers: List<MCPServerConfig>,
    serverStatuses: Map<String, MCPServerStatus>,
    enabledCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onImportClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onToggleServer: (String) -> Unit,
    onHealthCheck: (String) -> Unit,
    onDeleteServer: (MCPServerConfig) -> Unit,
    context: android.content.Context
) {
    val colorScheme = MaterialTheme.colorScheme
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Status Bar - matching the image exactly
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (enabledCount > 0) 
                    Color(0xFF1E3A5F).copy(alpha = 0.3f) 
                else 
                    colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // MCP Icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = "${servers.size} ${if (servers.size == 1) "server" else "servers"} configured",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "$enabledCount enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Refresh button
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRefreshing) "Refreshing…" else "Refresh", fontSize = 13.sp)
                        }
                        
                        // Import JSON button
                        OutlinedButton(
                            onClick = onImportClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON", fontSize = 13.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Add Server button
                    Button(
                        onClick = onAddServerClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Server", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        
        // Base Servers Section
        if (baseServers.isNotEmpty()) {
            item {
                Text(
                    text = "Base Servers (${baseServers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            
            items(baseServers, key = { it.id }) { server ->
                ServerCardNew(
                    server = server,
                    status = serverStatuses[server.id],
                    isSelected = server.enabled,
                    onToggle = { onToggleServer(server.id) },
                    onHealthCheck = { onHealthCheck(server.id) },
                    onDelete = null, // Base servers can't be deleted
                    isHfMcp = MCPUtils.isHfMcpEndpoint(server.url)
                )
            }
        }
        
        // Custom Servers Section
        item {
            Text(
                text = "Custom Servers (${customServers.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        if (customServers.isEmpty()) {
            // Empty state - matching the image exactly
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, colorScheme.outline.copy(alpha = 0.3f)),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No custom servers yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Add your own MCP servers with custom tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAddServerClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Your First Server")
                        }
                    }
                }
            }
        } else {
            items(customServers, key = { it.id }) { server ->
                ServerCardNew(
                    server = server,
                    status = serverStatuses[server.id],
                    isSelected = server.enabled,
                    onToggle = { onToggleServer(server.id) },
                    onHealthCheck = { onHealthCheck(server.id) },
                    onDelete = { onDeleteServer(server) },
                    isHfMcp = false
                )
            }
        }
        
        // Quick Tips Section - matching the image exactly
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💡 Quick Tips",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    QuickTipItem("Only connect to servers you trust")
                    QuickTipItem("Enable servers to make their tools available in chat")
                    QuickTipItem("Use the Health Check button to verify server connectivity")
                    QuickTipItem("You can add HTTP headers for authentication when required")
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun QuickTipItem(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun ServerCardNew(
    server: MCPServerConfig,
    status: MCPServerStatus?,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onHealthCheck: () -> Unit,
    onDelete: (() -> Unit)?,
    isHfMcp: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    var isLoadingHealth by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    
    val tools by MCPManager.tools.collectAsState()
    val serverTools = tools.filter { it.serverId == server.id }
    
    val statusInfo = when (status?.state) {
        MCPConnectionState.CONNECTED -> Triple("Connected", Color(0xFF22C55E), Color(0xFF22C55E).copy(alpha = 0.1f))
        MCPConnectionState.STANDBY -> Triple("Standby", Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.1f))
        MCPConnectionState.CONNECTING -> Triple("Connecting...", Color(0xFF3B82F6), Color(0xFF3B82F6).copy(alpha = 0.1f))
        MCPConnectionState.ERROR -> Triple("Error", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.1f))
        else -> Triple("Unknown", colorScheme.onSurfaceVariant, colorScheme.surfaceVariant)
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) 
            Color(0xFF1E3A5F).copy(alpha = 0.2f) 
        else 
            colorScheme.surfaceVariant,
        border = if (isSelected) 
            BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f)) 
        else 
            null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Switch(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF3B82F6)
                    )
                )
            }
            
            // Status badge and tools count
            if (status != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = statusInfo.third
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusInfo.second)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusInfo.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusInfo.second,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Tools count
                    if (serverTools.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${serverTools.size} ${if (serverTools.size == 1) "tool" else "tools"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Error message
            if (status?.state == MCPConnectionState.ERROR && status.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.1f)
                ) {
                    Text(
                        text = status.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            
            // Action buttons
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Health Check button
                OutlinedButton(
                    onClick = {
                        isLoadingHealth = true
                        onHealthCheck()
                        isLoadingHealth = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Health Check", fontSize = 12.sp)
                }
                
                // Settings button for HF MCP
                if (isHfMcp) {
                    OutlinedButton(
                        onClick = { /* Open HF settings */ },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settings", fontSize = 12.sp)
                    }
                }
                
                // Delete button for custom servers
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }
            
            // Available Tools (expandable)
            if (serverTools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTools = !showTools },
                    color = Color.Transparent
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Available Tools (${serverTools.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Icon(
                            if (showTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                AnimatedVisibility(visible = showTools) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        serverTools.forEach { tool ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = tool.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface
                                )
                                if (tool.description != null) {
                                    Text(
                                        text = " - ${tool.description}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportConfigForm(
    onCancel: () -> Unit,
    onImport: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var jsonText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Upload JSON File area
        Text(
            text = "Upload JSON File",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Upload,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Click to upload or drag and drop",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "mcp_config.json or similar",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        
        // OR divider
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "or",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(20.dp))
        
        // Paste JSON Configuration
        Text(
            text = "Paste JSON Configuration",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = jsonText,
                onValueChange = { 
                    jsonText = it
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = {
                    Text(
                        text = """
{
  "mcpServers": {
    "time": {
      "command": "uvx",
      "args": ["mcp-server-time"]
    },
    "mongodb": {
      "command": "npx",
      "args": ["-y", "mongodb-mcp-server"],
      "env": {
        "MDB_MCP_CONNECTION_STRING": "mongodb://..."
      }
    }
  }
}
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
        
        // Security Warning
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFEF3C7),
            border = BorderStroke(1.dp, Color(0xFFFCD34D).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Important Security Notice",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = "This will run commands on your server. Only import configurations from trusted sources. Malicious configurations can execute arbitrary code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF92400E)
                    )
                }
            }
        }
        
        // Error message
        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.1f)
            ) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // Action buttons
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    if (jsonText.isBlank()) {
                        error = "Please provide JSON configuration"
                    } else {
                        onImport(jsonText)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB)
                )
            ) {
                Text("Import Configuration")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServerForm(
    onCancel: () -> Unit,
    onSubmit: (name: String, url: String, headers: Map<String, String>) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showHeaders by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Server Name
        Text(
            text = "Server Name",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariant
        )
        Text(
            text = "*",
            color = Color(0xFFEF4444)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("My MCP Server") },
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Server URL
        Row {
            Text(
                text = "Server URL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariant
            )
            Text(" *", color = Color(0xFFEF4444))
        }
        Spacer(modifier = Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://example.com/mcp") },
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // HTTP Headers (expandable)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showHeaders = !showHeaders },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.3f)),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HTTP Headers (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant
                )
                Icon(
                    if (showHeaders) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
        
        AnimatedVisibility(visible = showHeaders) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (headers.isEmpty()) {
                    Text(
                        text = "No headers configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    headers.forEachIndexed { index, (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { newKey ->
                                    headers = headers.toMutableList().apply {
                                        this[index] = newKey to value
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Header name") },
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    headers = headers.toMutableList().apply {
                                        this[index] = key to newValue
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Value") },
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    headers = headers.toMutableList().apply { removeAt(index) }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { headers = headers + ("" to "") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Header")
                }
            }
        }
        
        // Security Warning
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFEF3C7),
            border = BorderStroke(1.dp, Color(0xFFFCD34D).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Be careful with custom MCP servers.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = "They receive your requests (including conversation context and any headers you add) and can run powerful tools on your behalf. Only add servers you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF92400E)
                    )
                }
            }
        }
        
        // Error
        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.1f)
            ) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // Action buttons
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    when {
                        name.isBlank() -> error = "Server name is required"
                        url.isBlank() -> error = "Server URL is required"
                        !MCPUtils.validateMcpServerUrl(url) -> error = "Invalid URL"
                        else -> {
                            val headersMap = headers
                                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                                .toMap()
                            onSubmit(name.trim(), url.trim(), headersMap)
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB)
                )
            ) {
                Text("Add Server")
            }
        }
    }
}
