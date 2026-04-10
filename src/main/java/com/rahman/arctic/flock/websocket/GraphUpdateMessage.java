package com.rahman.arctic.flock.websocket;

public class GraphUpdateMessage {

	private String nodes;
	private String links;
	private String senderName;
	private String exerciseName;

	public String getNodes() { return nodes; }
	public void setNodes(String nodes) { this.nodes = nodes; }

	public String getLinks() { return links; }
	public void setLinks(String links) { this.links = links; }

	public String getSenderName() { return senderName; }
	public void setSenderName(String senderName) { this.senderName = senderName; }

	public String getExerciseName() { return exerciseName; }
	public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

}
