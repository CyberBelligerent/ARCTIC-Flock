package com.rahman.arctic.flock.rest.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.shard.ShardManager;
import com.rahman.arctic.shard.objects.ShardProviderReference;

@RestController
@RequestMapping("/range-api/v1/admin/providers")
public class AdminProviderRestController {

	private final ShardManager shardManager;

	public AdminProviderRestController(ShardManager shardManager) {
		this.shardManager = shardManager;
	}

	@GetMapping("/")
	ResponseEntity<List<ShardProviderReference>> listProviders() {
		return new ResponseEntity<>(shardManager.listProviders(), HttpStatus.OK);
	}

	@PostMapping("/reload")
	ResponseEntity<?> reloadAllProviders() {
		shardManager.reloadAllProviders();
		return new ResponseEntity<>("Providers reloaded", HttpStatus.OK);
	}

	@PostMapping("/{name}/reload")
	ResponseEntity<?> reloadProvider(@PathVariable String name) {
		shardManager.reloadProvider(name);
		return new ResponseEntity<>("Provider reloaded: " + name, HttpStatus.OK);
	}

	@PostMapping("/{name}/disable")
	ResponseEntity<?> disableProvider(@PathVariable String name) {
		shardManager.disableProvider(name);
		return new ResponseEntity<>("Provider disabled: " + name, HttpStatus.OK);
	}

}
