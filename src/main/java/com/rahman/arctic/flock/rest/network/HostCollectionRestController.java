package com.rahman.arctic.flock.rest.network;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.objects.computers.HostCollection;
import com.rahman.arctic.iceberg.objects.computers.HostCollectionDTO;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.iceberg.repos.HostCollectionRepo;

@RestController
@RequestMapping("/range-api/v1")
public class HostCollectionRestController {

	@Autowired
	private ExerciseRepo exRepo;

	@Autowired
	private HostCollectionRepo collectionRepo;

	@GetMapping("/exercise/{name}/host-collection")
	ResponseEntity<Set<HostCollection>> getCollections(@PathVariable String name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		return ResponseEntity.ok(range.getHostCollections());
	}

	@PostMapping(value = "/exercise/{name}/host-collection", produces = "application/json", consumes = "application/json")
	ResponseEntity<RangeExercise> addCollection(@PathVariable String name, @RequestBody HostCollectionDTO dto) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));

		HostCollection existing = range.getHostCollections().stream()
				.filter(c -> dto.getName() != null && dto.getName().equals(c.getName()))
				.findAny().orElse(null);
		if (existing != null) {
			applyDto(existing, dto);
			collectionRepo.save(existing);
			return ResponseEntity.ok(range);
		}

		HostCollection hc = new HostCollection();
		hc.setRangeId(range.getId());
		applyDto(hc, dto);
		collectionRepo.save(hc);
		range.getHostCollections().add(hc);

		exRepo.save(range);
		return ResponseEntity.ok(range);
	}

	@PutMapping(value = "/exercise/{name}/host-collection/{collectionName}", produces = "application/json", consumes = "application/json")
	ResponseEntity<HostCollection> updateCollection(@PathVariable String name, @PathVariable String collectionName,
			@RequestBody HostCollectionDTO dto) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		HostCollection hc = range.getHostCollections().stream()
				.filter(c -> collectionName.equals(c.getName())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Collection Not Found With Name: " + collectionName));

		applyDto(hc, dto);
		collectionRepo.save(hc);
		return ResponseEntity.ok(hc);
	}

	@DeleteMapping("/exercise/{name}/host-collection/{collectionName}")
	ResponseEntity<RangeExercise> removeCollection(@PathVariable String name, @PathVariable String collectionName) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		HostCollection hc = range.getHostCollections().stream()
				.filter(c -> collectionName.equals(c.getName())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Collection Not Found With Name: " + collectionName));
		range.getHostCollections().remove(hc);
		exRepo.save(range);
		collectionRepo.delete(hc);
		return ResponseEntity.ok(range);
	}

	private void applyDto(HostCollection hc, HostCollectionDTO dto) {
		hc.setName(dto.getName());
		hc.setCount(Math.max(1, dto.getCount()));
		hc.setMapId(dto.getMapId());
		hc.setOsType(dto.getOsType());
		hc.setNetworks(dto.getNetworks());
		hc.setVolumes(dto.getVolumes());
		hc.setExtraVariables(dto.getExtraVariables());
	}

}
