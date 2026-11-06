"""The existing CurseForge workflow now publishes the six pinned free 0.3.6 files."""
import sys
from publish_core036 import main

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Stopped:', str(error), flush=True)
        sys.exit(1)
