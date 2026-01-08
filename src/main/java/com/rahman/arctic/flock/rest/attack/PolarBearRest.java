package com.rahman.arctic.flock.rest.attack;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.polarbear.objects.AttackStep;
import com.rahman.arctic.polarbear.objects.AttackStepDTO;
import com.rahman.arctic.polarbear.objects.AttackStepRef;
import com.rahman.arctic.polarbear.objects.StepItem;
import com.rahman.arctic.polarbear.repos.AttackStepRepo;

@RestController
@RequestMapping("/polarbear-api/v1")
public class PolarBearRest {

	@Autowired
	private ExerciseRepo exRepo;
	
	@Autowired
	private AttackStepRepo asRepo;
	
	@PostMapping(value="/attackstep/{exercise}", produces="application/json", consumes="application/json")
	ResponseEntity<?> createAttackStep(@RequestBody AttackStepDTO dto, @PathVariable String exercise) {
		// Get the range
		RangeExercise range = exRepo.findByName(exercise.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + exercise));
		
		// Create a new AttackStep based on Data Transfer Object
		AttackStep as = new AttackStep();
		as.setTitle(dto.getTitle());
		
		// Create a reference object to the newly created AttackStep
		AttackStepRef asr = new AttackStepRef(as.getId());
		
//		// Save the attack step reference to the range
//		range.getAttackSteps().add(asr);
		
		// Save the AttackStep to the database
		AttackStep ass = asRepo.save(as);
		
		// Return the newly created/saved AttackStep as a 201 message
		return new ResponseEntity<>(ass, HttpStatus.CREATED);
	}
	
	@PostMapping(value="/attackstep/{exercise}/{attackstep}", produces="application/json", consumes="application/json")
	ResponseEntity<?> addStepItem(@RequestBody StepItem item, @PathVariable String exercise, String attackstep) {
		// Get the range
		RangeExercise range = exRepo.findByName(exercise.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + exercise));
		
//		// Ensure the AttackStep is in the range
//		if(!range.doesAttackStepsContainId(attackstep)) return new ResponseEntity<>("Range Does Not Contain AttackStep: " + attackstep, HttpStatus.NOT_FOUND);
//		
		// Make sure the AttackStep even exists
		AttackStep as = asRepo.findById(attackstep).orElseThrow(() -> new ResourceNotFoundException("AttackStep Not Found With ID: " + attackstep));
		as.getBody().add(item);
		
		// Save the newly modified AttackStep and return it
		AttackStep ass = asRepo.save(as);
		return new ResponseEntity<>(ass, HttpStatus.CREATED);
	}
	
	@GetMapping("/attackstep/{exercise}")
	ResponseEntity<?> getAttackSteps(@PathVariable String exercise) {
		// Get the range
		RangeExercise range = exRepo.findByName(exercise.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + exercise));
		
		// Obtain all of the AttackStepReferences and create a list of the actual attacksteps
		List<AttackStep> attackSteps = new ArrayList<>();
//		range.getAttackSteps().forEach((e) -> {
//			attackSteps.add(asRepo.findById(e.getAttackStepId()).get());
//		});
		
		// Return a JSON list of all of the attackSteps of a range
		return new ResponseEntity<>(attackSteps, HttpStatus.OK);
	}
	
}