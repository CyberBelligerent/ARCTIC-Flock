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

import com.rahman.arctic.flock.exceptions.ResourceAlreadyExistsException;
import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.objects.computers.ArcticHost;
import com.rahman.arctic.iceberg.objects.computers.ArcticHostDTO;
import com.rahman.arctic.iceberg.repos.ArcticHostRepo;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;

@RestController
@RequestMapping("/range-api/v1")
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

		ArcticHost existing = range.getHosts().stream()
				.filter(h -> dto.getName() != null && dto.getName().equals(h.getName()))
				.findAny().orElse(null);
		if (existing != null) {
			existing.setMapId(dto.getMapId());
			existing.setCount(dto.getCount());
			existing.setOsType(dto.getOsType());
			existing.setNetworks(dto.getNetworks());
			existing.setVolumes(dto.getVolumes());
			existing.setExtraVariables(dto.getExtraVariables());
			hostRepo.save(existing);
			return ResponseEntity.ok(range);
		}

		ArcticHost ah = new ArcticHost();
		ah.setName(dto.getName());
		ah.setMapId(dto.getMapId());
		ah.setOsType(dto.getOsType());
		ah.setNetworks(dto.getNetworks());
		ah.setVolumes(dto.getVolumes());
		ah.setExtraVariables(dto.getExtraVariables());
		hostRepo.save(ah);
		range.getHosts().add(ah);

		exRepo.save(range);
		return ResponseEntity.ok(range);
	}

	@PutMapping(value = "/exercise/{name}/host/{hostName}", produces = "application/json", consumes = "application/json")
	ResponseEntity<ArcticHost> updateHost(@PathVariable String name, @PathVariable String hostName, @RequestBody ArcticHostDTO dto) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		ArcticHost host = range.getHosts().stream().filter(h -> hostName.equals(h.getName())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Host Not Found With Name: " + hostName));

		host.setName(dto.getName());
		host.setMapId(dto.getMapId());
		host.setCount(dto.getCount());
		host.setOsType(dto.getOsType());
		host.setNetworks(dto.getNetworks());
		host.setVolumes(dto.getVolumes());
		host.setExtraVariables(dto.getExtraVariables());

		hostRepo.save(host);
		return ResponseEntity.ok(host);
	}

	@DeleteMapping(value = "/exercise/{name}/host/{n_name}")
	ResponseEntity<RangeExercise> removeHost(@PathVariable String name, @PathVariable String n_name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		ArcticHost an = range.getHosts().stream().filter(e -> n_name.equals(e.getName())).findAny().orElseThrow(() -> new ResourceNotFoundException("Host Not Found With Name: " + n_name));
		range.getHosts().remove(an);
		exRepo.save(range);
		hostRepo.delete(an);
		return ResponseEntity.ok(range);
	}
	
}