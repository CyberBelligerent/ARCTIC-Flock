package com.rahman.arctic.flock.rest.provider_resources;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.shard.ShardManager;
import com.rahman.arctic.shard.objects.providers.ProviderFlavor;
import com.rahman.arctic.shard.objects.providers.ProviderImage;

@RestController
@RequestMapping("/range-api/v1/provider")
public class ProviderRestfulGetters {

	@Autowired
	private ShardManager sm;
	
	@GetMapping("/images")
	ResponseEntity<List<ProviderImage>> getImages() {
		CompletableFuture<List<ProviderImage>> cf = sm.getPrimaryShard().obtainOS();
		
		List<ProviderImage> images;
        try {
            images = cf.get(); // Wait for the CompletableFuture to complete
        } catch (Exception e) {
            // Handle exception (e.g., TimeoutException, ExecutionException)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // Return the response with fetched images
        return ResponseEntity.ok(images);
	}
	
	@GetMapping("/flavors")
	ResponseEntity<List<ProviderFlavor>> getFlavors() {
		CompletableFuture<List<ProviderFlavor>> cf = sm.getPrimaryShard().obtainFlavors();
		
		List<ProviderFlavor> flavors;
        try {
        	flavors = cf.get(); // Wait for the CompletableFuture to complete
        } catch (Exception e) {
            // Handle exception (e.g., TimeoutException, ExecutionException)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // Return the response with fetched images
        return ResponseEntity.ok(flavors);
	}
	
}