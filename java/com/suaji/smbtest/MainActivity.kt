
class MainActivity : AppCompatActivity() {
    
    private lateinit var ipAddressInput: TextInputEditText
    private lateinit var shareNameInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var testConnectionButton: Button
    private lateinit var resultText: TextView
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val TAG = "SUAJI_SMB_DEBUG"
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Paksa light mode (anti dark mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        // Set status bar & navigation bar transparent, dan fullscreen
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        initializeViews()
        setupClickListeners()
        checkPermissions()
        
        // Log app start
        logToFile("=== EZ SMB Test App Started ===")
        logToFile("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        logToFile("Android Version: ${android.os.Build.VERSION.RELEASE}")
    }
    
    private fun initializeViews() {
        ipAddressInput = findViewById(R.id.ipAddressInput)
        shareNameInput = findViewById(R.id.shareNameInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        testConnectionButton = findViewById(R.id.testConnectionButton)
        resultText = findViewById(R.id.resultText)
    }
    
    private fun setupClickListeners() {
        testConnectionButton.setOnClickListener {
            testSmbConnection()
        }
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun logToFile(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] $message"
            
            // Log ke Android Logcat
            Log.d(TAG, message)
            
            // Save ke app's external files dir (no extra permission needed)
            val logFile = File(getExternalFilesDir(null), "ez_smb_debug.log")
            FileWriter(logFile, true).use { writer ->
                writer.append(logMessage).append("\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to log file: ${e.message}")
        }
    }
    
    private fun testSmbConnection() {
        val ipAddress = ipAddressInput.text.toString().trim()
        val shareName = shareNameInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        
        if (ipAddress.isEmpty() || shareName.isEmpty()) {
            resultText.text = "Sila isi IP Address dan Share Name"
            return
        }
        
        testConnectionButton.isEnabled = false
        resultText.text = "Testing connection...\nIP: $ipAddress\nShare: $shareName\nUser: $username"
        
        // Log test start
        logToFile("=== SMB Connection Test Started ===")
        logToFile("IP Address: $ipAddress")
        logToFile("Share Name: $shareName")
        logToFile("Username: $username")
        logToFile("Password: ${if (password.isNotEmpty()) "***" else "empty"}")
        
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    // Test network connectivity dahulu
                    logToFile("Testing network connectivity...")
                    val networkTest = testNetworkConnectivity(ipAddress)
                    if (!networkTest.first) {
                        logToFile("Network test FAILED: ${networkTest.second}")
                        return@withContext networkTest.second
                    }
                    
                    logToFile("Network test PASSED")
                    
                    // Test SMB connection
                    logToFile("Testing SMB connection...")
                    testSmbConnectionInternal(ipAddress, shareName, username, password)
                }
                resultText.text = result
                logToFile("Test completed successfully")
            } catch (e: Exception) {
                val errorMsg = "Error: ${e.message}"
                resultText.text = errorMsg
                logToFile("Test FAILED with exception: ${e.message}")
                logToFile("Exception type: ${e.javaClass.simpleName}")
                e.printStackTrace()
            } finally {
                testConnectionButton.isEnabled = true
            }
        }
    }
    
    private fun testNetworkConnectivity(ipAddress: String): Pair<Boolean, String> {
        return try {
            logToFile("Attempting to connect to $ipAddress:445")
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(ipAddress, 445), 5000) // SMB port
            socket.close()
            logToFile("Socket connection successful")
            Pair(true, "")
        } catch (e: Exception) {
            logToFile("Socket connection FAILED: ${e.message}")
            val errorMsg = StringBuilder()
            errorMsg.append("🌐 NETWORK CONNECTION FAILED!\n\n")
            errorMsg.append("Cannot connect to $ipAddress:445 (SMB port)\n")
            errorMsg.append("Error: ${e.message}\n\n")
            errorMsg.append("🔧 TROUBLESHOOTING:\n")
            errorMsg.append("1. Check IP address betul\n")
            errorMsg.append("2. Pastikan komputer dalam network yang sama\n")
            errorMsg.append("3. Check Windows Firewall:\n")
            errorMsg.append("   - Allow port 445 (SMB)\n")
            errorMsg.append("   - Allow File and Printer Sharing\n")
            errorMsg.append("4. Test ping: ping $ipAddress\n")
            errorMsg.append("5. Check Windows SMB service running\n")
            errorMsg.append("6. Cuba disable Windows Firewall temporarily\n")
            
            Pair(false, errorMsg.toString())
        }
    }
    
    private fun testSmbConnectionInternal(ipAddress: String, shareName: String, username: String, password: String): String {
        return try {
            // Buat SMB URL
            val smbUrl = "smb://$ipAddress/$shareName/"
            logToFile("SMB URL: $smbUrl")
            
            // Buat authentication
            val auth = if (username.isNotEmpty()) {
                logToFile("Using authenticated access with user: $username")
                NtlmPasswordAuthentication("", username, password)
            } else {
                logToFile("Using anonymous access")
                NtlmPasswordAuthentication.ANONYMOUS
            }
            
            // Buat SmbFile object
            logToFile("Creating SmbFile object...")
            val smbFile = SmbFile(smbUrl, auth)
            logToFile("SmbFile object created successfully")
            
            // Test connection dengan list files
            logToFile("Attempting to list files...")
            val files = smbFile.listFiles()
            logToFile("File listing successful, found ${files.size} items")
            
            val result = StringBuilder()
            result.append("✅ Connection BERJAYA!\n\n")
            result.append("Server: $ipAddress\n")
            result.append("Share: $shareName\n")
            result.append("User: $username\n")
            result.append("URL: $smbUrl\n")
            result.append("Files dalam root directory:\n")
            
            if (files.isEmpty()) {
                result.append("- (Empty directory)\n")
                logToFile("Directory is empty")
            } else {
                files.forEach { file ->
                    val type = if (file.isDirectory) "[DIR]" else "[FILE]"
                    result.append("- $type ${file.name}\n")
                    logToFile("Found: $type ${file.name}")
                }
            }
            
            logToFile("SMB connection test completed successfully")
            result.toString()
            
        } catch (e: Exception) {
            logToFile("SMB connection FAILED: ${e.message}")
            logToFile("Exception type: ${e.javaClass.simpleName}")
            
            val errorMsg = StringBuilder()
            errorMsg.append("❌ Connection GAGAL!\n\n")
            errorMsg.append("Error: ${e.message}\n")
            errorMsg.append("Error Type: ${e.javaClass.simpleName}\n\n")
            
            // Specific error handling
            when {
                e.message?.contains("Access is denied", ignoreCase = true) == true -> {
                    errorMsg.append("🔒 ACCESS DENIED - Masalah permissions:\n")
                    errorMsg.append("1. Check Windows account permissions\n")
                    errorMsg.append("2. Pastikan user ada access ke folder\n")
                    errorMsg.append("3. Cuba guna Administrator account\n")
                }
                e.message?.contains("Connection refused", ignoreCase = true) == true -> {
                    errorMsg.append("🌐 CONNECTION REFUSED - Masalah network:\n")
                    errorMsg.append("1. Check IP address betul\n")
                    errorMsg.append("2. Pastikan komputer dalam network yang sama\n")
                    errorMsg.append("3. Check Windows Firewall\n")
                }
                e.message?.contains("Authentication failed", ignoreCase = true) == true -> {
                    errorMsg.append("🔑 AUTHENTICATION FAILED - Masalah credentials:\n")
                    errorMsg.append("1. Check username dan password betul\n")
                    errorMsg.append("2. Cuba format: COMPUTERNAME\\Username\n")
                    errorMsg.append("3. Pastikan account tidak locked\n")
                }
                e.message?.contains("not found", ignoreCase = true) == true -> {
                    errorMsg.append("📁 SHARE NOT FOUND - Masalah share name:\n")
                    errorMsg.append("1. Check share name betul\n")
                    errorMsg.append("2. Pastikan folder sudah di-share\n")
                    errorMsg.append("3. Cuba guna 'SharedDocs' atau 'C$'\n")
                }
            }
            
            errorMsg.append("\n📋 CHECKLIST untuk Windows 10/11:\n")
            errorMsg.append("1. Enable Network Discovery:\n")
            errorMsg.append("   - Control Panel > Network > Network and Sharing Center\n")
            errorMsg.append("   - Advanced sharing settings > Turn on network discovery\n")
            errorMsg.append("2. Enable File Sharing:\n")
            errorMsg.append("   - Advanced sharing settings > Turn on file and printer sharing\n")
            errorMsg.append("3. Check Windows Credentials:\n")
            errorMsg.append("   - Guna username: COMPUTERNAME\\Username\n")
            errorMsg.append("   - Atau guna local account password\n")
            errorMsg.append("4. Enable SMB:\n")
            errorMsg.append("   - Run: optionalfeatures.exe\n")
            errorMsg.append("   - Enable SMB 1.0/CIFS File Sharing Support\n")
            errorMsg.append("5. Check Firewall:\n")
            errorMsg.append("   - Allow File and Printer Sharing\n")
            errorMsg.append("6. Test Share Permissions:\n")
            errorMsg.append("   - Right-click folder > Properties > Sharing\n")
            errorMsg.append("   - Add user dengan Full Control\n")
            errorMsg.append("7. Test dengan IP address yang betul\n")
            errorMsg.append("8. Cuba guna 'net share' command untuk list shares\n")
            errorMsg.append("\n📱 Log file saved to: /storage/emulated/0/ez_smb_debug.log\n")
            
            errorMsg.toString()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        logToFile("=== App Shutting Down ===")
        coroutineScope.cancel()
    }
}