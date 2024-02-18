package com.rahman.arctic.flock.rest.network;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.flock.exceptions.ResourceAlreadyExistsException;
import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.objects.computers.ArcticHost;
import com.rahman.arctic.iceberg.objects.computers.ArcticHostDTO;
import com.rahman.arctic.iceberg.repos.ArcticHostRepo;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;

@RestController
@RequestMapping("/iceberg-api/v1")
public class IcebergHostRestController {

	@Autowired
	private ExerciseRepo exRepo;

	@Autowired
	private ArcticHostRepo hostRepo;

	@GetMapping("/exercise/{name}/host")
	ResponseEntity<Set<ArcticHost>> getExerciseHosts(@PathVariable String name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		return ResponseEntity.ok(range.getHosts());
	}

	@PostMapping(value = "/exercise/{name}/host", produces = "application/json", consumes = "application/json")
	ResponseEntity<RangeExercise> addHost(@PathVariable String name, @RequestBody ArcticHostDTO dto) throws ResourceAlreadyExistsException {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		if (range.getNetworks().stream().anyMatch(e -> e.getNetName() == dto.getName())) {
			throw new ResourceAlreadyExistsException("Host with name: " + dto.getName() + " already exists!");
		}
		ArcticHost ah = new ArcticHost();
		ah.setName(dto.getName());
		ah.setMapId(dto.getMapId());
		ah.setFlavorId(dto.getFlavorId());
		ah.setImageId(dto.getImageId());
		ah.setSize(dto.getSize());
		ah.setNetworks(dto.getNetworks());
		ah.setVolumes(dto.getVolumes());
		hostRepo.save(ah);
		range.getHosts().add(ah);

		exRepo.save(range);
		return ResponseEntity.ok(range);
	}

	@DeleteMapping(value = "/exercise/{name}/host/{n_name}")
	ResponseEntity<RangeExercise> removeHost(@PathVariable String name, @PathVariable String n_name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		ArcticHost an = range.getHosts().stream().filter(e -> e.getName() == n_name).findAny().orElseThrow(() -> new ResourceNotFoundException("Network Not Found With Name: " + n_name));
		range.getHosts().remove(an);
		exRepo.save(range);
		hostRepo.delete(an);
		return ResponseEntity.ok(range);
	}
	
}