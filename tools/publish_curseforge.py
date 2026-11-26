"""Publish the six pinned free 0.3.8 release files through the existing workflow."""
import sys
from publish_core038 import main
if __name__ == '__main__':
    try:main()
    except Exception as error:
        print('Stopped:',str(error),flush=True)
        sys.exit(1)
