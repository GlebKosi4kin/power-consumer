.PHONY: build up down logs clean generate restart status

build:
	docker-compose build

up: build
	docker-compose up -d
	@echo "Waiting for services to start..."
	@sleep 5
	docker-compose run --rm generator
	@echo ""
	@echo "Services started:"
	@echo "  Frontend:  http://localhost"
	@echo "  Backend:   http://localhost:9000"
	@echo "  MQTT:      localhost:1883"
	@echo "  Postgres:  localhost:5432"

down:
	docker-compose down

generate:
	docker-compose run --rm generator

logs:
	docker-compose logs -f

logs-backend:
	docker-compose logs -f backend

logs-generator:
	docker-compose logs -f generator

clean:
	docker-compose down -v --remove-orphans

restart:
	docker-compose restart

status:
	docker-compose ps
