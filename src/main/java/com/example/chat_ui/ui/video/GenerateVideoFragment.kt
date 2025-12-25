package com.example.chat_ui.ui.video

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.chat_ui.R
import com.example.chat_ui.api.VeoVideoClient
import com.example.chat_ui.databinding.FragmentGenerateVideoBinding
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

/**
 * Generate Video Fragment - Independent video generation screen
 * 
 * Features:
 * - Mode selection (Text/Image/Video-to-Video)
 * - Public/Private visibility selector with YouTube OAuth
 * - Advanced settings (expandable)
 * - Real-time parameter validation
 * - Progress tracking with job polling
 * - Media picker integration
 */
class GenerateVideoFragment : Fragment() {
    
    private var _binding: FragmentGenerateVideoBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GenerateVideoViewModel by viewModels()
    
    // Media picker launchers
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.setSelectedImage(requireContext(), uri)
            }
        }
    }
    
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.setSelectedVideo(requireContext(), uri)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenerateVideoBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModel()
        setupModeSelector()
        setupVisibilitySelector()
        setupAdvancedSettings()
        setupActionButtons()
    }
    
    private fun setupUI() {
        // Setup video model spinner (ONLY models confirmed available)
        val availableVideoModels = listOf(
            "veo-3.1-generate-preview" to "Veo 3.1 (Preview)",
            "veo-3.1-fast-generate-preview" to "Veo 3.1 Fast (Preview)",
            "veo-3.0-generate-001" to "Veo 3",
            "veo-3.0-fast-generate-001" to "Veo 3 Fast",
            "veo-2.0-generate-001" to "Veo 2"
        )

        val modelAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            availableVideoModels.map { it.second }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.videoModelSpinner.adapter = modelAdapter
        binding.videoModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val modelId = availableVideoModels.getOrNull(position)?.first ?: return
                viewModel.updateParams { copy(modelId = modelId) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no-op
            }
        }

        // Setup quality spinner
        val qualityAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.video_quality_options,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.qualitySpinner.adapter = qualityAdapter
        
        // Setup aspect ratio chips
        setupAspectRatioChips()
        
        // Setup duration slider
        binding.durationSlider.apply {
            valueFrom = 4f
            valueTo = 10f
            stepSize = 1f
            value = 6f
            
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    viewModel.updateParams {
                        copy(durationSeconds = slider.value.toInt())
                    }
                }
            })
        }
    }
    
    private fun observeViewModel() {
        // Observe generation state
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                handleGenerationState(state)
            }
        }
        
        // Observe parameters
        lifecycleScope.launch {
            viewModel.params.collect { params ->
                updateUIFromParams(params)
            }
        }
        
        // Observe UI state
        lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                updateUIState(uiState)
            }
        }
        
        // Observe generation validation
        lifecycleScope.launch {
            viewModel.canGenerate.collect { canGenerate ->
                binding.generateButton.isEnabled = canGenerate
            }
        }
    }
    
    private fun setupModeSelector() {
        binding.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when (checkedIds.firstOrNull()) {
                R.id.chipTextMode -> VeoVideoClient.VideoMode.TEXT_TO_VIDEO
                R.id.chipImageMode -> VeoVideoClient.VideoMode.IMAGE_TO_VIDEO
                R.id.chipVideoMode -> VeoVideoClient.VideoMode.VIDEO_TO_VIDEO
                else -> VeoVideoClient.VideoMode.TEXT_TO_VIDEO
            }
            
            viewModel.updateParams { copy(mode = mode) }
            updateModeSpecificUI(mode)
        }
    }
    
    private fun setupVisibilitySelector() {
        binding.visibilityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val visibility = when (checkedId) {
                R.id.radioPrivate -> VeoVideoClient.VideoVisibility.PRIVATE
                R.id.radioPublic -> VeoVideoClient.VideoVisibility.PUBLIC
                else -> VeoVideoClient.VideoVisibility.PUBLIC
            }
            
            viewModel.updateParams { copy(visibility = visibility) }
            
            // Show YouTube warning for public videos
            binding.youtubeWarning.visibility = if (visibility == VeoVideoClient.VideoVisibility.PUBLIC) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
    
    private fun setupAdvancedSettings() {
        // Toggle advanced settings
        binding.advancedSettingsToggle.setOnClickListener {
            viewModel.toggleAdvancedSettings()
        }
        
        // Setup style spinners
        setupStyleSpinners()
        
        // Setup FPS selector
        binding.fpsChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val fps = when (checkedIds.firstOrNull()) {
                R.id.chipFps24 -> 24
                R.id.chipFps30 -> 30
                else -> null
            }
            viewModel.updateParams { copy(fps = fps) }
        }
    }
    
    private fun setupActionButtons() {
        // Generate button - default behavior (may be overridden temporarily by state)
        binding.generateButton.setOnClickListener {
            viewModel.generateVideo(requireContext())
        }
        
        // Media picker buttons
        binding.selectImageButton.setOnClickListener {
            openImagePicker()
        }
        
        binding.selectVideoButton.setOnClickListener {
            openVideoPicker()
        }
        
        // Clear button
        binding.clearButton.setOnClickListener {
            viewModel.clearGeneration()
        }

        // Cancel button
        binding.cancelButton.setOnClickListener {
            viewModel.cancelGeneration()
        }
        
        // Prompt input listener
        binding.promptInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updateParams { copy(prompt = s.toString()) }
            }
        })
    }
    
    private fun handleGenerationState(state: GenerateVideoState) {
        when (state) {
            is GenerateVideoState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = getString(R.string.ready_to_generate)
                binding.generateButton.text = getString(R.string.generate_video_button)
                binding.generateButton.isEnabled = true
                binding.generateButton.setOnClickListener {
                    viewModel.generateVideo(requireContext())
                }

                binding.cancelButton.visibility = View.GONE
            }
            
            is GenerateVideoState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.starting_generation)
                binding.generateButton.text = getString(R.string.generating_video_button)
                binding.generateButton.isEnabled = false
                binding.generateButton.setOnClickListener(null)

                binding.cancelButton.visibility = View.VISIBLE
                binding.cancelButton.isEnabled = true
            }
            
            is GenerateVideoState.Polling -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.generating_video, state.progress)
                binding.generateButton.text = getString(R.string.generating_video_button)
                binding.generateButton.isEnabled = false
                binding.generateButton.setOnClickListener(null)

                binding.cancelButton.visibility = View.VISIBLE
                binding.cancelButton.isEnabled = true
            }
            
            is GenerateVideoState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = getString(R.string.video_generated_success)
                binding.generateButton.text = getString(R.string.generate_another)
                binding.generateButton.isEnabled = true
                binding.generateButton.setOnClickListener {
                    viewModel.clearGeneration()
                }

                binding.cancelButton.visibility = View.GONE

                // Show success and navigate to result
                showGenerationResult(state.result)
            }
            
            is GenerateVideoState.NeedsYouTubeAuth -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = getString(R.string.youtube_auth_required)
                binding.generateButton.text = getString(R.string.authenticate_youtube)
                binding.generateButton.isEnabled = true
                binding.generateButton.setOnClickListener {
                    openYouTubeAuth(state.authUrl)
                }

                binding.cancelButton.visibility = View.GONE
            }

            is GenerateVideoState.NeedsConfiguration -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = state.message
                binding.generateButton.text = getString(R.string.api_settings)
                binding.generateButton.isEnabled = true

                binding.cancelButton.visibility = View.GONE

                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()

                // Navigate user to API settings screen
                binding.generateButton.setOnClickListener {
                    val intent = Intent(requireContext(), com.example.chat_ui.MainActivity::class.java).apply {
                        putExtra("open_route", com.example.chat_ui.navigation.NavRoutes.ApiSettings.route)
                    }
                    startActivity(intent)
                }
            }

            is GenerateVideoState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = getString(R.string.generation_error, state.message)
                binding.generateButton.text = getString(R.string.try_again)
                binding.generateButton.isEnabled = true
                binding.generateButton.setOnClickListener {
                    viewModel.generateVideo(requireContext())
                }

                binding.cancelButton.visibility = View.GONE

                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateUIFromParams(params: VideoGenerationParams) {
        // Update prompt
        if (binding.promptInput.text.toString() != params.prompt) {
            binding.promptInput.setText(params.prompt)
        }
        
        // Update mode
        val modeChipId = when (params.mode) {
            VeoVideoClient.VideoMode.TEXT_TO_VIDEO -> R.id.chipTextMode
            VeoVideoClient.VideoMode.IMAGE_TO_VIDEO -> R.id.chipImageMode
            VeoVideoClient.VideoMode.VIDEO_TO_VIDEO -> R.id.chipVideoMode
        }
        binding.modeChipGroup.check(modeChipId)
        
        // Update visibility
        val visibilityRadioId = when (params.visibility) {
            VeoVideoClient.VideoVisibility.PRIVATE -> R.id.radioPrivate
            VeoVideoClient.VideoVisibility.PUBLIC -> R.id.radioPublic
        }
        binding.visibilityRadioGroup.check(visibilityRadioId)
        
        // Update duration
        binding.durationSlider.value = params.durationSeconds.toFloat()
        binding.durationText.text = getString(R.string.duration_seconds, params.durationSeconds)
        
        // Update selected media info
        updateSelectedMediaInfo(params)
    }
    
    private fun updateUIState(uiState: GenerateVideoUiState) {
        // Show/hide advanced settings
        binding.advancedSettingsContainer.visibility = if (uiState.showAdvancedSettings) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Update cost estimate
        uiState.estimatedCost?.let {
            binding.costEstimate.text = getString(R.string.estimated_cost, it)
            binding.costEstimate.visibility = View.VISIBLE
        } ?: run {
            binding.costEstimate.visibility = View.GONE
        }
    }
    
    private fun updateModeSpecificUI(mode: VeoVideoClient.VideoMode) {
        when (mode) {
            VeoVideoClient.VideoMode.TEXT_TO_VIDEO -> {
                binding.mediaPickerSection.visibility = View.GONE
                binding.promptInputLayout.hint = getString(R.string.prompt_hint_text)
            }
            
            VeoVideoClient.VideoMode.IMAGE_TO_VIDEO -> {
                binding.mediaPickerSection.visibility = View.VISIBLE
                binding.selectImageButton.visibility = View.VISIBLE
                binding.selectVideoButton.visibility = View.GONE
                binding.promptInputLayout.hint = getString(R.string.prompt_hint_image)
            }
            
            VeoVideoClient.VideoMode.VIDEO_TO_VIDEO -> {
                binding.mediaPickerSection.visibility = View.VISIBLE
                binding.selectImageButton.visibility = View.GONE
                binding.selectVideoButton.visibility = View.VISIBLE
                binding.promptInputLayout.hint = getString(R.string.prompt_hint_video)
            }
        }
    }
    
    private fun updateSelectedMediaInfo(params: VideoGenerationParams) {
        binding.selectedMediaInfo.visibility = when {
            params.selectedImageUri != null -> {
                binding.selectedMediaText.text = getString(R.string.image_selected)
                View.VISIBLE
            }
            params.selectedVideoUri != null -> {
                binding.selectedMediaText.text = getString(R.string.video_selected)
                View.VISIBLE
            }
            else -> View.GONE
        }
    }
    
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }
    
    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
            type = "video/*"
        }
        videoPickerLauncher.launch(intent)
    }
    
    private fun openYouTubeAuth(authUrl: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(requireContext(), Uri.parse(authUrl))
            
            // Handle auth completion (simplified - in real app you'd handle the callback properly)
            Toast.makeText(requireContext(), getString(R.string.youtube_auth_complete_prompt), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.failed_to_open_authentication), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showGenerationResult(result: VeoVideoClient.VideoGenerationResult) {
        // Navigate to video player or show result dialog
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra("video_id", result.id)
            putExtra("video_url", result.url)
            putExtra("video_prompt", result.prompt)
            putExtra("video_duration", result.duration)
        }
        startActivity(intent)
    }
    
    private fun setupAspectRatioChips() {
        val aspectRatios = listOf("9:16", "16:9", "1:1")
        aspectRatios.forEach { ratio ->
            val chip = Chip(requireContext()).apply {
                text = ratio
                isCheckable = true
                setOnClickListener {
                    viewModel.updateParams { copy(aspectRatio = ratio) }
                }
            }
            binding.aspectRatioChipGroup.addView(chip)
        }
        
        // Set default selection
        (binding.aspectRatioChipGroup.getChildAt(1) as Chip).isChecked = true
    }
    
    private fun setupStyleSpinners() {
        // Cinematic style spinner
        val cinematicStyles = VeoVideoClient.CinematicStyle.values().map { it.description }
        val cinematicAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cinematicStyles)
        cinematicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.cinematicStyleSpinner.adapter = cinematicAdapter
        
        // Motion level spinner
        val motionLevels = VeoVideoClient.MotionLevel.values().map { it.description }
        val motionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, motionLevels)
        motionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.motionLevelSpinner.adapter = motionAdapter
        
        // Lighting style spinner
        val lightingStyles = VeoVideoClient.LightingStyle.values().map { it.description }
        val lightingAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lightingStyles)
        lightingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.lightingStyleSpinner.adapter = lightingAdapter
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
