env:
	docker build -t mathuin/android .

clean:
	docker run --rm -v $(HOME)/git/mathuin/FinleyBreese/FinleyBreese:/app/src mathuin/android /app/build.py clean

debug:
	docker run --rm -v $(HOME)/git/mathuin/FinleyBreese/FinleyBreese:/app/src mathuin/android /app/build.py debug

release:
	docker run --rm -itv $(HOME)/git/mathuin/FinleyBreese/FinleyBreese:/app/src -v $(HOME)/keys:/keys mathuin/android /app/build.py release

bash:
	docker run --rm -itv $(HOME)/git/mathuin/FinleyBreese/FinleyBreese:/app/src -v $(HOME)/keys:/keys mathuin/android bash

