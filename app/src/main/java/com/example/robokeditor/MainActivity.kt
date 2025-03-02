
package com.example.robokeditor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.robokeditor.databinding.ActivityMainBinding
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import com.example.robokeditor.lang.*
public class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    
    private val binding: ActivityMainBinding
      get() = checkNotNull(_binding) { "Activity has been destroyed" }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        
        binding.parent.setColorScheme(SchemeDarcula())
        binding.parent.setEditorLanguage(JavaLanguage())
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
