package com.nexora.ai.society

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val MasxBackground = Color(0xFF0B0D16)
private val MasxPanel = Color(0xFF171B2B)
private val MasxAccent = Color(0xFF75D7E8)

/** Native MAS-X control center. Every action is backed by local database or runtime services. */
@Composable
fun MasxHome(viewModel: MasxViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf("Overview") }
    var skillName by remember { mutableStateOf("") }
    var skillDescription by remember { mutableStateOf("") }
    var skillInstructions by remember { mutableStateOf("") }
    val tabs = listOf("Overview","Agents","Tasks","Models","Memory","Skills","Tools","Security","Performance","Evolution","Settings")

    Column(Modifier.fillMaxSize().background(MasxBackground).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("MICRO-AGENT SOCIETY", color=Color.White, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                Text("LOCAL AGENTS · SHARED MODEL · GOVERNED ACTIONS", color=Color(0xFF9B9CB2), style=MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick=onBack) { Text("Back to chat", color=MasxAccent) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            tabs.forEach { item ->
                FilterChip(selected=tab==item,onClick={tab=item},label={Text(item)})
            }
        }
        state.error?.let { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF3B202A))) {
            Text(it,color=Color(0xFFFFC5CF),modifier=Modifier.padding(12.dp))
        } }
        when(tab) {
            "Overview" -> OverviewPanel(state,viewModel)
            "Agents" -> AgentsPanel(state,viewModel)
            "Tasks" -> TasksPanel(state,viewModel)
            "Models" -> ModelPanel(state,viewModel)
            "Memory" -> MemoryPanel(state,viewModel)
            "Skills" -> SkillsPanel(state,viewModel,skillName,{skillName=it},skillDescription,{skillDescription=it},skillInstructions,{skillInstructions=it})
            "Tools" -> ToolsPanel(state,viewModel)
            "Security" -> SecurityPanel(state)
            "Performance" -> PerformancePanel(state)
            "Evolution" -> EvolutionPanel(state,viewModel)
            "Settings" -> SettingsPanel(state,viewModel)
        }
    }
}

@Composable
private fun OverviewPanel(state: MasxUiState, viewModel: MasxViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MetricCard("Agents",state.agents.size.toString(),Modifier.weight(1f))
            MetricCard("Tasks",state.tasks.size.toString(),Modifier.weight(1f))
            MetricCard("Running",state.tasks.count { it.status==TaskStatus.RUNNING }.toString(),Modifier.weight(1f))
        }
        Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Shared local intelligence",color=Color.White,fontWeight=FontWeight.SemiBold)
                Text(if(state.modelReady) "LiteRT-LM model ready" else "Model not ready — open chat and install/load the local model",color=if(state.modelReady) Color(0xFF9CF0D8) else Color(0xFFFFD28A))
                Text("One application-scoped runtime · one heavyweight inference at a time · queued agent requests",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
            }
        }
        state.resources?.let { resource ->
            Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Device resource governor",color=Color.White,fontWeight=FontWeight.SemiBold)
                    Text("Available RAM: ${resource.availableMemoryMb} MB · Low-memory: ${resource.lowMemory}",color=Color(0xFFB9B4CA))
                    Text("Battery: ${resource.batteryPercent?.let { "$it%" } ?: "unavailable"} · Power saver: ${resource.powerSaveMode}",color=Color(0xFFB9B4CA))
                    Text("Thermal status: ${resource.thermalStatus?.toString() ?: "not exposed on this Android version"}",color=Color(0xFFB9B4CA))
                    TextButton(onClick=viewModel::refreshResources) { Text("Refresh measurements") }
                }
            }
        }
        Text("Departments",color=Color.White,style=MaterialTheme.typography.titleMedium)
        Text("Queued LAMP messages: ${state.queuedMessages}",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        MasxDepartment.entries.forEach { department ->
            val count=state.agents.count { it.department==department }
            Text("${department.title} · $count agents",color=Color(0xFFB9B4CA),modifier=Modifier.padding(vertical=3.dp))
        }
    }
}

@Composable
private fun MetricCard(title: String,value: String,modifier: Modifier=Modifier) {
    Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=MasxPanel)) {
        Column(Modifier.padding(12.dp)) {
            Text(value,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(title,color=Color(0xFF9B9CB2),style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AgentsPanel(state: MasxUiState,viewModel: MasxViewModel) {
    var department by remember { mutableStateOf<MasxDepartment?>(null) }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("") }
    var newInstructions by remember { mutableStateOf("") }
    var newDepartment by remember { mutableStateOf(MasxDepartment.PRODUCTIVITY) }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Create temporary agent (starts paused)",color=Color.White,fontWeight=FontWeight.SemiBold)
                OutlinedTextField(newName,{newName=it.take(80)},label={Text("Agent name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(newRole,{newRole=it.take(120)},label={Text("Role")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(newInstructions,{newInstructions=it.take(8000)},label={Text("Instructions")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=3)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    MasxDepartment.entries.forEach { d -> FilterChip(selected=newDepartment==d,onClick={newDepartment=d},label={Text(d.title)}) }
                }
                Button(onClick={viewModel.proposeTemporaryAgent(newName,newDepartment,newRole,newInstructions);newName="";newRole="";newInstructions=""},enabled=newName.isNotBlank() && newRole.isNotBlank() && newInstructions.isNotBlank()) { Text("Save paused agent") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(selected=department==null,onClick={department=null},label={Text("All")})
            MasxDepartment.entries.forEach { d -> FilterChip(selected=department==d,onClick={department=if(department==d)null else d},label={Text(d.title)}) }
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(state.agents.filter { department==null || it.department==department },key={it.id}) { agent ->
                Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(agent.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                                Text("${agent.department.title} · ${agent.level.name} · ${agent.role}",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked=agent.status==AgentStatus.ACTIVE,onCheckedChange={ enabled -> if(agent.level==AgentLevel.TEMPORARY && enabled) viewModel.approveTemporaryAgent(agent.id) else viewModel.setAgentStatus(agent.id,if(enabled)AgentStatus.ACTIVE else AgentStatus.PAUSED)})
                        }
                        Text(agent.instructions,color=Color(0xFFB9B4CA),style=MaterialTheme.typography.bodySmall)
                        Text("Capabilities: ${agent.capabilities.sorted().joinToString()}",color=MasxAccent,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksPanel(state: MasxUiState,viewModel: MasxViewModel) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value=state.taskTitle,onValueChange=viewModel::updateTaskTitle,label={Text("Task title")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(value=state.taskInput,onValueChange=viewModel::updateTaskInput,label={Text("Task instructions")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
        Button(onClick=viewModel::createTask,enabled=!state.isBusy && state.taskInput.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text(if(state.isBusy)"Working…" else "Create persistent task") }
        state.selectedTaskId?.let { selected ->
            val task=state.tasks.firstOrNull { it.id==selected }
            Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Selected task · ${task?.status?.name ?: "loading"}",color=MasxAccent)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick=viewModel::runSelectedTask,enabled=!state.isBusy && state.modelReady && task?.status != TaskStatus.COMPLETED && task?.status != TaskStatus.CANCELLED && task?.status != TaskStatus.PAUSED) { Text("Run locally") }
                        TextButton(onClick=viewModel::pauseSelectedTask,enabled=!state.isBusy && (task?.status==TaskStatus.READY || task?.status==TaskStatus.ASSIGNED || task?.status==TaskStatus.RUNNING || task?.status==TaskStatus.PLANNING)) { Text("Pause") }
                        TextButton(onClick=viewModel::resumeSelectedTask,enabled=!state.isBusy && task?.status==TaskStatus.PAUSED) { Text("Resume") }
                        TextButton(onClick=viewModel::cancelSelectedTask,enabled=task?.status != TaskStatus.COMPLETED && task?.status != TaskStatus.CANCELLED) {
                            Text(if(state.isBusy && task?.status==TaskStatus.RUNNING) "Cancel run" else "Cancel")
                        }
                    }
                    Text("Add prerequisite task (selected task waits until it completes)",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        state.tasks.filter { it.id!=selected && it.status!=TaskStatus.COMPLETED && it.status!=TaskStatus.CANCELLED }.forEach { dependency ->
                            FilterChip(selected=false,onClick={viewModel.addTaskDependency(selected,dependency.id)},label={Text(dependency.title.take(18))})
                        }
                    }
                    state.result?.let { Text(it,color=Color.White,style=MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Text("Saved tasks",color=Color.White,fontWeight=FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(state.tasks,key={it.id}) { task ->
                Card(colors=CardDefaults.cardColors(containerColor=if(state.selectedTaskId==task.id) Color(0xFF292344) else MasxPanel),
                    modifier=Modifier.fillMaxWidth().clickable { viewModel.selectTask(task.id) }) {
                    Column(Modifier.padding(10.dp)) {
                        Text(task.title,color=Color.White,fontWeight=FontWeight.Medium)
                        Text("${task.status} · ${task.priority} · attempt ${task.attempt}/${task.maxAttempts}",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.labelSmall)
                        task.error?.let { Text(it,color=Color(0xFFFF9EAD),style=MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPanel(state: MasxUiState,viewModel: MasxViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("AIMS-X model registry · model roles route to the one shared local runtime. Dynamic multi-model loading is not enabled yet.",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        state.models.forEach { model ->
            Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(model.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                    Text("${model.family} · ${model.format} · ${model.status}",color=MasxAccent,style=MaterialTheme.typography.labelSmall)
                    Text("Runtime: ${model.runtimeId}",color=Color(0xFFB9B4CA),style=MaterialTheme.typography.bodySmall)
                    Text("Source: ${model.source}",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
                    Text("License: ${model.license ?: "Not recorded"}",color=Color(0xFFFFD28A),style=MaterialTheme.typography.bodySmall)
                    Text("Preflight: " + (state.compatibility[model.id]?.status?.name ?: "Pending"),color=Color(0xFF9CF0D8),style=MaterialTheme.typography.labelSmall)
                    state.compatibility[model.id]?.reasons?.forEach { reason -> Text(reason,color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Text("Role assignments",color=Color.White,fontWeight=FontWeight.SemiBold)
        ModelRole.entries.forEach { role ->
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${role.name.replace('_',' ')} · ${state.models.firstOrNull { it.id==state.modelRoles[role] }?.name ?: "Unassigned"}",color=Color.White)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    state.models.forEach { model ->
                        FilterChip(
                            selected=state.modelRoles[role]==model.id,
                            onClick={viewModel.assignModelRole(role,model.id)},
                            label={Text(model.name)}
                        )
                    }
                }
            }
        }
        Text("The primary Gemma artifact is installed and loaded from the chat model installer. Other entries must be explicitly imported and validated before they can be activated.",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MemoryPanel(state: MasxUiState,viewModel: MasxViewModel) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("User-approved shared memory is isolated from agent-private and task memory. Agents can read it only when the corresponding setting is enabled.",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(key,{key=it.take(160)},label={Text("Memory key")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(value,{value=it.take(20_000)},label={Text("Memory value")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
        Button(onClick={viewModel.saveApprovedMemory(key,value);key="";value=""},enabled=key.isNotBlank() && value.isNotBlank()) { Text("Save user-approved memory") }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(state.memories,key={it.id}) { memory ->
                Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(memory.key,color=Color.White,fontWeight=FontWeight.SemiBold)
                        Text("${memory.scope} · ${memory.ownerId ?: "shared"}",color=MasxAccent,style=MaterialTheme.typography.labelSmall)
                        Text(memory.value,color=Color(0xFFB9B4CA),style=MaterialTheme.typography.bodySmall)
                        Text("Source: ${memory.provenance}",color=Color(0xFF70748C),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillsPanel(
    state: MasxUiState,viewModel: MasxViewModel,
    name: String,onName:(String)->Unit,description:String,onDescription:(String)->Unit,
    instructions:String,onInstructions:(String)->Unit
) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Skills are declarative instruction bundles. They cannot install executable code or grant tools.",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(name,onName,label={Text("Skill name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(description,onDescription,label={Text("Description")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(instructions,onInstructions,label={Text("Instructions")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
        Button(onClick={
            val id=name.lowercase().replace(Regex("[^a-z0-9]+"),".").trim('.').take(60)
            if(id.length>=3 && name.isNotBlank() && instructions.isNotBlank()) {
                viewModel.installSkillDraft(id,name.trim(),description.trim(),instructions.trim())
                onName("");onDescription("");onInstructions("")
            }
        },enabled=name.isNotBlank() && instructions.isNotBlank()) { Text("Save skill draft") }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(state.skills,key={it.id}) { skill ->
                Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(skill.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                        Text("v${skill.version} · ${skill.status}",color=MasxAccent,style=MaterialTheme.typography.labelSmall)
                        Text(skill.description,color=Color(0xFFB9B4CA),style=MaterialTheme.typography.bodySmall)
                        if(skill.status==SkillStatus.DRAFT || skill.status==SkillStatus.DISABLED) {
                            TextButton(onClick={viewModel.approveSkill(skill.id)}) { Text("Validate and approve") }
                        }
                        if(skill.status==SkillStatus.ACTIVE) {
                            Text("Assign to active agent",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.labelSmall)
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                state.agents.filter { it.status==AgentStatus.ACTIVE }.forEach { agent ->
                                    FilterChip(selected=false,onClick={viewModel.assignSkillToAgent(skill.id,agent.id)},label={Text(agent.name)})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsPanel(state: MasxUiState,viewModel: MasxViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Permission switches only govern registered handlers. Enabling a policy does not create a tool implementation.",color=Color(0xFFFFD28A),style=MaterialTheme.typography.bodySmall)
        state.toolPermissions.forEach { tool ->
            Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(tool.toolId,color=Color.White,fontWeight=FontWeight.Medium)
                        Text("${tool.risk} · approval required: ${tool.approvalRequired}",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked=tool.enabled,onCheckedChange={viewModel.setToolEnabled(tool.toolId,it)})
                }
            }
        }
    }
}

@Composable
private fun SecurityPanel(state: MasxUiState) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Local audit trail · permission changes, task transitions, skill activation and proposal reviews.",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(state.auditEvents,key={it.id}) { event ->
                Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(event.action,color=Color.White,fontWeight=FontWeight.SemiBold)
                        Text("${event.outcome} · ${event.actorId ?: "system"} · ${event.subjectId ?: "no subject"}",color=MasxAccent,style=MaterialTheme.typography.labelSmall)
                        Text(event.details,color=Color(0xFFB9B4CA),style=MaterialTheme.typography.bodySmall)
                        Text("Recorded: ${event.createdAt}",color=Color(0xFF70748C),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformancePanel(state: MasxUiState) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        state.resources?.let { resource ->
            MetricCard("Available RAM", "${resource.availableMemoryMb} MB",Modifier.fillMaxWidth())
            Text("Battery ${resource.batteryPercent?.let { "$it%" } ?: "N/A"} · Thermal ${resource.thermalStatus ?: "N/A"} · Power saver ${resource.powerSaveMode}",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        }
        Text("Measured task and inference metrics",color=Color.White,fontWeight=FontWeight.SemiBold)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(state.metrics,key={it.id}) { metric ->
                Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(metric.metric.replace('_',' '),color=Color.White,fontWeight=FontWeight.Medium)
                        Text("${metric.value} · ${metric.agentId}",color=MasxAccent,style=MaterialTheme.typography.bodySmall)
                        Text("Task: ${metric.taskId ?: "device"} · ${metric.recordedAt}",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvolutionPanel(state: MasxUiState,viewModel: MasxViewModel) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Controlled evolution only: MAS-X can propose changes, but proposals do not edit source code, permissions, model weights or security policy.",color=Color(0xFFFFD28A),style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(title,{title=it.take(120)},label={Text("Proposal title")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(description,{description=it.take(8000)},label={Text("Change, reason and verification plan")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
        Button(onClick={viewModel.createEvolutionProposal(title,description);title="";description=""},enabled=title.isNotBlank() && description.isNotBlank()) { Text("Submit proposal for review") }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(state.evolutionProposals,key={it.id}) { proposal ->
                Card(colors=CardDefaults.cardColors(containerColor=MasxPanel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(proposal.title,color=Color.White,fontWeight=FontWeight.SemiBold)
                        Text(proposal.status,color=MasxAccent,style=MaterialTheme.typography.labelSmall)
                        Text(proposal.description,color=Color(0xFFB9B4CA),style=MaterialTheme.typography.bodySmall)
                        if(proposal.status=="PROPOSED") {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={viewModel.reviewEvolutionProposal(proposal.id,true)}) { Text("Approve for implementation") }
                                TextButton(onClick={viewModel.reviewEvolutionProposal(proposal.id,false)}) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(state: MasxUiState,viewModel: MasxViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SettingSwitch("Society enabled","society.enabled",state,viewModel)
        SettingSwitch("Allow user-approved shared memory","memory.allow_user_approved",state,viewModel)
        SettingSwitch("Require approval for local writes","security.require_approval_for_writes",state,viewModel)
        Text("Security audit logging is always enabled in this build.",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        Text("Society mode",color=Color.White,fontWeight=FontWeight.SemiBold)
        val modes=listOf("SUPERVISOR","TEAM","DEBATE","RESEARCH","DEVELOPER","RECOVERY","SIMULATION")
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            modes.forEach { mode -> FilterChip(selected=state.settings["society.mode"]==mode,onClick={viewModel.setSetting("society.mode",mode)},label={Text(mode)}) }
        }
        Text("Maximum registered agents: ${state.settings["society.max_agents"] ?: "24"}",color=Color(0xFFB9B4CA))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("12","24","48").forEach { count -> FilterChip(selected=(state.settings["society.max_agents"] ?: "24")==count,onClick={viewModel.setSetting("society.max_agents",count)},label={Text(count)}) }
        }
        Text("Maximum inference concurrency: 1 (shared runtime safety default)",color=Color(0xFF9B9CB2),style=MaterialTheme.typography.bodySmall)
        Text("Task retry limit: ${state.settings["tasks.max_attempts"] ?: "2"}",color=Color(0xFFB9B4CA))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("0","1","2","3").forEach { count -> FilterChip(selected=(state.settings["tasks.max_attempts"] ?: "2")==count,onClick={viewModel.setSetting("tasks.max_attempts",count)},label={Text(count)}) }
        }
        Text("Changes are stored in the app-private MAS-X database.",color=Color(0xFF70748C),style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingSwitch(
    title:String,key:String,state:MasxUiState,viewModel:MasxViewModel,defaultValue:Boolean=false
) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(title,color=Color.White,modifier=Modifier.weight(1f))
        Switch(
            checked=(state.settings[key] ?: defaultValue.toString())=="true",
            onCheckedChange={viewModel.setSetting(key,it.toString())}
        )
    }
}
