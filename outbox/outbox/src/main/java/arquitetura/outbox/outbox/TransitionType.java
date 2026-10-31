package arquitetura.outbox.outbox;

public enum TransitionType {
	USER_CREATED("user.created"),
	USER_UPDATED("user.updated"),
	USER_DELETED("user.deleted");

	private final String routingKey;

	TransitionType(String routingKey) {
		this.routingKey = routingKey;
	}

	public String getRoutingKey() {
		return routingKey;
	}
}
