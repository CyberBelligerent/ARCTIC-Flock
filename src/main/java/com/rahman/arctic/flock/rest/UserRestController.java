package com.rahman.arctic.flock.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.flock.exceptions.ResourceAlreadyExistsException;
import com.rahman.arctic.orca.objects.RangeUser;
import com.rahman.arctic.orca.objects.UserDTO;
import com.rahman.arctic.orca.objects.role.Role;
import com.rahman.arctic.orca.objects.role.UserRole;
import com.rahman.arctic.orca.repos.RoleRepo;
import com.rahman.arctic.orca.repos.UserRepo;
import com.rahman.arctic.orca.utils.IUserDetails;
import com.rahman.arctic.orca.utils.IUserService;
import com.rahman.arctic.orca.utils.JwtRequest;
import com.rahman.arctic.orca.utils.JwtResponse;
import com.rahman.arctic.orca.utils.JwtTokenUtil;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/range-api/v1")
public class UserRestController {
	
	@Autowired
	private DaoAuthenticationProvider authManager;
	
	@Autowired
	private JwtTokenUtil tokenUtil;
	
	private IUserService userService;
	private UserRepo userRepo;
	private RoleRepo roleRepo;
	
	public UserRestController(IUserService uServ, UserRepo ur, RoleRepo rr) {
		userService = uServ;
		userRepo = ur;
		roleRepo = rr;
	}
	
	@PostMapping(value = "/user", consumes = "application/json", produces = "application/json")
	ResponseEntity<?> createUser(@RequestBody UserDTO dto) throws ResourceAlreadyExistsException {
		RangeUser test = userRepo.findByUsernameIgnoreCase(dto.getUsername()).orElse(null);
		if(test != null) throw new ResourceAlreadyExistsException("User with username: " + dto.getUsername() + " already exists!");
		final RangeUser ru = new RangeUser();
		ru.setName(dto.getName());
		ru.setUsername(dto.getUsername());
		ru.setPassword(new BCryptPasswordEncoder().encode(dto.getPassword()));
		ru.setPasswordLastReset(new Date());
		
		List<Role> roles = new ArrayList<>();
		
		if(dto.getRole().equals("user")) {
			roles.add(roleRepo.findByRole(UserRole.USER));
		} else if(dto.getRole().equals("admin")) {
			roles.add(roleRepo.findByRole(UserRole.ADMIN));
		} else {
			return ResponseEntity.notFound().build();
		}
		
		ru.setUserRoles(roles);
		userRepo.save(ru);
		
		// TODO: Make it so that the Provider can check to see if it is able to create users
		// this is good for services like OpenStack or Proxmox
		// it is not doable for things like Azure or AWS
		
//		os.submitIcebergSingleton(client -> {
//			client.identity().users().create(Builders.user()
//					.name(dto.getUsername())
//					.password(dto.getPassword())
//					.defaultProjectId(dto.getProjectId())
//					.build());
//		});
		
		return ResponseEntity.ok().build();
	}
	
	@PostMapping("/regularUser")
	ResponseEntity<?> isRegularUser() {
		return ResponseEntity.ok().build();
	}
	
	@PostMapping("/authenticate")
	ResponseEntity<?> authenticateUser(HttpServletRequest httpRequest, @RequestBody JwtRequest request) {
		try {
			authenticate(request.getUsername(), request.getPassword());
			IUserDetails user = (IUserDetails)userService.loadUserByUsername(request.getUsername());
			String token = tokenUtil.generateToken(user, httpRequest.getRemoteAddr());
			return new ResponseEntity<>(new JwtResponse(token), HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>("Failed", HttpStatus.BAD_REQUEST);
		}
	}
	
	private void authenticate(String username, String password) throws Exception {
		Objects.requireNonNull(username);
		Objects.requireNonNull(password);
		try {
			authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		} catch (DisabledException e) {
			throw new Exception("User_Disabled", e);
		} catch (BadCredentialsException e) {
			throw new Exception("Invalid_Credentials", e);
		}
	}
	
}