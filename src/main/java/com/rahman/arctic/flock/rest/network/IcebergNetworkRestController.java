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
import com.rahman.arctic.iceberg.objects.computers.ArcticNetwork;
import com.rahman.arctic.iceberg.objects.computers.ArcticNetworkDTO;
import com.rahman.arctic.iceberg.repos.ArcticNetworkRepo;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;

@RestController
@RequestMapping("/iceberg-api/v1")
public class IcebergNetworkRestController {

	@Autowired
	private ExerciseRepo exRepo;

	@Autowired
	private ArcticNetworkRepo netRepo;

	/**
	 * Returns a list of all ArcticNetworks attached to a RangeExercise
	 * 
	 * @param name Name of the Exercise of which to grab all the ArcticNetwork
	 *             objects
	 * @return List of networks in a JSON Format for a specified exercise
	 */
	@GetMapping("/exercise/{name}/network")
	ResponseEntity<Set<ArcticNetwork>> getExerciseNetworks(@PathVariable String name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		return ResponseEntity.ok(range.getNetworks());
	}

	/**
	 * Creates an ArcticNetwork object stored in the database that will later be
	 * used to create the OpenStack network pre-configured when exercise builds
	 * 
	 * @param name Name of the Exercise of which to create the network on
	 * @param dto  ArcticNetworkDTO object that is used to transfer the information
	 *             to create the new ArcticNetwork Entity
	 * @return The ExerciseRange object for information and viewing
	 * @throws ResourceAlreadyExistsException Throws this warning if a network by
	 *                                        the name already exists in the
	 *                                        Exercise. While OpenStack can handle
	 *                                        this, it is mainly a QoL to ensure
	 *                                        people are not getting confused on the
	 *                                        names of their networks.
	 */
	@PostMapping(value = "/exercise/{name}/network", produces = "application/json", consumes = "application/json")
	ResponseEntity<RangeExercise> addNetwork(@PathVariable String name, @RequestBody ArcticNetworkDTO dto) throws ResourceAlreadyExistsException {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		if (range.getNetworks().stream().anyMatch(e -> e.getNetName() == dto.getName())) {
			throw new ResourceAlreadyExistsException("Network with name: " + dto.getName() + " already exists!");
		}
		ArcticNetwork an = new ArcticNetwork();
		an.setNetName(dto.getName());
		an.setMapId(dto.getMapId());
		an.setNetStart(dto.getStart());
		an.setNetEnd(dto.getEnd());
		an.setNetCidr(dto.getCidr());
		an.setGateway(dto.getGateway());
		netRepo.save(an);
		range.getNetworks().add(an);

		exRepo.save(range);
		return ResponseEntity.ok(range);
	}

	/**
	 * Deletes the ArcticNetwork from the RangeExercise and the ArcticNetworkRepo.
	 * Some planning needs to go in for the deletion of the OpenStack network
	 * 
	 * @param name   Name of the Exercise of which the network will be deleted from
	 * @param n_name Name of the network to be deleted
	 * @return The ExerciseRange object for information and viewing
	 */
	@DeleteMapping(value = "/exercise/{name}/network/{n_name}")
	ResponseEntity<RangeExercise> removeNetwork(@PathVariable String name, @PathVariable String n_name) {
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		ArcticNetwork an = range.getNetworks().stream().filter(e -> e.getNetName() == n_name).findAny().orElseThrow(() -> new ResourceNotFoundException("Network Not Found With Name: " + n_name));
		range.getNetworks().remove(an);
		exRepo.save(range);
		netRepo.delete(an);
		return ResponseEntity.ok(range);
	}
	
}