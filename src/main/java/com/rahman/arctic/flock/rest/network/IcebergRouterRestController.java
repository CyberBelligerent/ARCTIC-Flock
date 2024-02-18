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
import com.rahman.arctic.iceberg.objects.computers.ArcticRouter;
import com.rahman.arctic.iceberg.objects.computers.ArcticRouterDTO;
import com.rahman.arctic.iceberg.repos.ArcticRouterRepo;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;

@RestController
@RequestMapping("/iceberg-api/v1")
public class IcebergRouterRestController {

	@Autowired
	private ExerciseRepo exRepo;

	@Autowired
	private ArcticRouterRepo routerRepo;

	@GetMapping("/exercise/{name}/router")
	ResponseEntity<Set<ArcticRouter>> getExerciseRouters(@PathVariable String name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		return ResponseEntity.ok(range.getRouters());
	}

	@PostMapping(value = "/exercise/{name}/router", produces = "application/json", consumes = "application/json")
	ResponseEntity<RangeExercise> addRouter(@PathVariable String name, @RequestBody ArcticRouterDTO dto) throws ResourceAlreadyExistsException {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		if (range.getNetworks().stream().anyMatch(e -> e.getNetName() == dto.getName())) {
			throw new ResourceAlreadyExistsException("Host with name: " + dto.getName() + " already exists!");
		}
		ArcticRouter ar = new ArcticRouter();
		ar.setName(dto.getName());
		ar.setMapId(dto.getMapId());
		ar.setNetworks(dto.getNetworks());
		routerRepo.save(ar);
		range.getRouters().add(ar);

		exRepo.save(range);
		return ResponseEntity.ok(range);
	}

	@DeleteMapping(value = "/exercise/{name}/router/{n_name}")
	ResponseEntity<RangeExercise> removeRouter(@PathVariable String name, @PathVariable String n_name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		ArcticRouter ar = range.getRouters().stream().filter(e -> e.getName() == n_name).findAny().orElseThrow(() -> new ResourceNotFoundException("Network Not Found With Name: " + n_name));
		range.getRouters().remove(ar);
		exRepo.save(range);
		routerRepo.delete(ar);
		return ResponseEntity.ok(range);
	}
	
}