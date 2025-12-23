package com.rahman.arctic.flock.rest.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.shard.ShardManager;
import com.rahman.arctic.shard.configuration.ShardConfigurationService;
import com.rahman.arctic.shard.configuration.ShardProfileReference;
import com.rahman.arctic.shard.configuration.ShardProfileSettingsReference;
import com.rahman.arctic.shard.configuration.persistence.ShardConfiguration;
import com.rahman.arctic.shard.configuration.persistence.ShardConfigurationType;
import com.rahman.arctic.shard.configuration.persistence.ShardProfile;
import com.rahman.arctic.shard.objects.ShardConfigurationSettingDTO;
import com.rahman.arctic.shard.repos.ShardProfileRepo;

@RestController
@RequestMapping("/range-api/v1/profile")
public class ShardProfileRestController {

	private final ShardProfileRepo profileRepo;
	private final ShardConfigurationService configService;
	private final ShardManager shardManager;

	public ShardProfileRestController(ShardProfileRepo srp, ShardConfigurationService spsr, ShardManager sm) {
		profileRepo = srp;
		configService = spsr;
		shardManager = sm;
	}
	
	@PostMapping(path = "/providers", consumes = "application/json")
	ResponseEntity<?> createProvider(@RequestBody ShardProfileReference spr) {
		ArcticUserDetails details = (ArcticUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		ShardProfile sp = new ShardProfile();
		sp.setDomain(spr.getDomain());
		sp.setProfileName(spr.getName());
		sp.setUsername(details.getUsername());
		
		return ResponseEntity.ok(profileRepo.save(sp));
	}
	
	@GetMapping(path = "/providers", produces="application/json")
	ResponseEntity<?> getProviders() {
		ArcticUserDetails details = (ArcticUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		List<ShardProfile> sp = profileRepo.findAllByUsername(details.getUsername());
	
		List<ShardProfileReference> shardProfiles = new ArrayList<>();
		
		for(ShardProfile profile : sp) {
			ShardProfileReference spr = new ShardProfileReference();
			spr.setName(profile.getProfileName());
			spr.setDomain(profile.getDomain());
			spr.setStatus("Error");
			
			ShardProfileSettingsReference spsr = configService.getAllConfigurationsForProfile(profile.getId());
		
			Map<String, String> values = new HashMap<>();
			List<ShardConfiguration> config = configService.getAllConfigurationOptions(profile.getDomain());
			
			for(ShardConfiguration sc : config) {
				if(spsr.hasConfiguration(sc.getConfigKey())) {
					if(sc.getConfigType().equals(ShardConfigurationType.PASSWORD)) {
						values.put(sc.getConfigKey(), "********************");
					} else {
						values.put(sc.getConfigKey(), spsr.getConfiguration(sc.getConfigKey()));
					}
				}
			}
			
			spr.setValues(values);
			
			shardProfiles.add(spr);
		}
		
		return ResponseEntity.ok(shardProfiles);
	}
	
	@PostMapping("/create/{domain}")
	ResponseEntity<?> createDomainProfile(@PathVariable(name = "domain", required = true) String domain) {
		ArcticUserDetails details = (ArcticUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		ShardProfile sp = new ShardProfile();
		sp.setDomain(domain);
		sp.setUsername(details.getUsername());
		sp.setProfileName("testProfile");
		
		return ResponseEntity.ok(profileRepo.save(sp));
	}
	
	@PostMapping(path = "/settings/{domain}", consumes="application/json")
	ResponseEntity<?> createSettingsForDomain(@PathVariable(name = "domain", required = true) String domain, @RequestBody ShardConfigurationSettingDTO setting) {
		ArcticUserDetails details = (ArcticUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		ShardProfile sp = profileRepo.findByUsernameAndDomain(details.getUsername(), domain).orElseThrow(() -> new ResourceNotFoundException("Unable to find provider configuration for: " + domain));
		
		try {
			configService.setConfiguration(sp.getId(), setting.getKey(), setting.getValue());
			return ResponseEntity.ok("Setting Saved");
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.badRequest().body("Unable to save setting");
		}
	}
	
	@PostMapping("/test-connection/{domain}")
	ResponseEntity<?> testConnection(@PathVariable(value="domain", required = true)String domain) {
		ArcticUserDetails details = (ArcticUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		ShardProfile sp = profileRepo.findByUsernameAndDomain(details.getUsername(), domain).orElseThrow(() -> new ResourceNotFoundException("Unable to find provider configuration for: " + domain));
		
		return ResponseEntity.ok(shardManager.performConnectionTest(sp));
	}

}
