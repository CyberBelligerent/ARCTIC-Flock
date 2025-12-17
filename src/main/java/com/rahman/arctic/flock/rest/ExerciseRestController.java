package com.rahman.arctic.flock.rest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.flock.exceptions.ResourceAlreadyExistsException;
import com.rahman.arctic.iceberg.objects.RangeDTO;
import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.objects.RangeType;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.iceberg.services.IcebergCreator;
import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.shard.configuration.persistence.ShardProfile;
import com.rahman.arctic.shard.repos.ShardProfileRepo;

@RestController
@RequestMapping("/range-api/v1")
public class ExerciseRestController {

	private final ObjectProvider<IcebergCreator> icebergCreatorProvider;
	private final ShardProfileRepo profileRepo;
	
	@Autowired
    public ExerciseRestController(ObjectProvider<IcebergCreator> icebergCreatorProvider, ShardProfileRepo spr) {
        this.icebergCreatorProvider = icebergCreatorProvider;
        profileRepo = spr;
    }
	
	@Autowired
	private ExerciseRepo exRepo;
	
	// TODO: Build
	@PostMapping("/exercise/{name}/build/{domain}")
	ResponseEntity<?> buildExercise(@PathVariable(value = "name", required = true) String name, @PathVariable(value = "domain", required = true)String domain) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		ArcticUserDetails details = (ArcticUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		ShardProfile sp = profileRepo.findByUsernameAndDomain(details.getUsername(), domain).orElseThrow(() -> new ResourceNotFoundException("Unable to find provider configuration for: " + domain));
		
		IcebergCreator ic = icebergCreatorProvider.getObject();
		ic.setProfile(sp);
		try {
			ic.attemptCreation();
		} catch (Exception e) {
			return new ResponseEntity<>("Error creating client", HttpStatus.BAD_REQUEST);
		}
		
		range.getNetworks().forEach(net -> {
			ic.createNetwork(net);
		});
		
		range.getRouters().forEach(rout -> {
			ic.createRouter(rout);
		});
		
		range.getHosts().forEach(host -> {
			ic.createHost(host);
		});
		
		ic.start();
		
		return new ResponseEntity<>("Started", HttpStatus.OK);
	}
	
	@GetMapping("/exercise")
	ResponseEntity<List<RangeExercise>> getAllExercises() {
		List<RangeExercise> ranges = exRepo.findAll();
		return new ResponseEntity<>(ranges, HttpStatus.OK);
	}
	
	@GetMapping("/exercise/{name}")
	ResponseEntity<RangeExercise> getExercise(@PathVariable(value = "name", required = true) String name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		return new ResponseEntity<>(range, HttpStatus.OK);
	}
	
	@DeleteMapping(value = "/exercise/{name}", produces = "application/json")
	ResponseEntity<?> deleteExercise(@PathVariable String name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		
		// TODO: Figure out how to delete based on Provider
//		osService.submitIcebergSingleton(client -> {
//			client.identity().projects().delete(range.getProjectId());
//		});
		exRepo.delete(range);
		return new ResponseEntity<>("", HttpStatus.OK);
	}
	
	@PostMapping(value = "/exercise", produces = "application/json", consumes = "application/json")
	ResponseEntity<RangeExercise> addExercise(@RequestBody RangeDTO dto) throws ResourceAlreadyExistsException {
		RangeExercise test = exRepo.findByName(dto.getRangeName().replaceAll(" ", "_")).orElse(null);
		if(test != null) throw new ResourceAlreadyExistsException("Range with name: " + dto.getRangeName() + " already exists!");
		RangeType rt = RangeType.valueOf(dto.getRangeType().toUpperCase());
		if(rt == null) new ResourceNotFoundException("Range Type Does Not Exist: " + dto.getRangeType());
		
		RangeExercise range = new RangeExercise();
		range.setName(dto.getRangeName().replaceAll(" ", "_"));
		range.setDescription(dto.getRangeDescription());
		range.setConcurrentRanges(dto.getConcurrentRanges());
		range.setType(RangeType.valueOf(dto.getRangeType().toString().toUpperCase()));
		Set<String> tags = new HashSet<String>();
		if(dto.getTags().size() > 0) tags.addAll(dto.getTags());
		range.setTags(tags);
		
		exRepo.save(range);
		
//		osService.<Project>submitIcebergSingleton(client -> {
//			Project proj = client.identity().projects().create(Builders.project()
//					.name(dto.getRangeName().replace(" ", "_"))
//					.description(dto.getRangeDescription())
//					.enabled(true)
//					.build());
//			
//			client.identity().roles().grantProjectUserRole(proj.getId(), "e4dda4bbc13a41fcbb8ff0ad3ea826d4", "89da81da8dc149b6a331dbd17a8529a0");
//			
//			range.setProjectId(proj.getId());
//			exRepo.save(range);
//		});
		
		return new ResponseEntity<>(range, HttpStatus.CREATED);
	}
	
}